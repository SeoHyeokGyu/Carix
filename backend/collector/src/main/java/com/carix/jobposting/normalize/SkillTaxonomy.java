package com.carix.jobposting.normalize;

import com.carix.jobposting.Skill;
import com.carix.jobposting.SkillAlias;
import com.carix.jobposting.SkillAliasRepository;
import com.carix.jobposting.SkillRepository;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * DB (core.skill 및 core.skill_alias) 기반으로 스킬 별칭 사전을 동적으로 로딩하여 복합 토큰 분리, 표준 표기 통일, 잡음 필터링을 수행하는 스킬
 * 정규화 엔진. (Aho-Corasick 고속 오토마타 및 Google RE2J 적용)
 */
@Slf4j
@Component
public class SkillTaxonomy {

  public static final String SKILL = "SKILL";
  public static final String JOB_CATEGORY = "JOB_CATEGORY";

  /** 복합 문자열 분리용 RE2J 정규식 */
  private static final Pattern DELIMITER_PATTERN =
      Pattern.compile("[,/\\\\;\n|\t&()·\\[\\]{}]|\\.(?=\\s|$)|(?<=\\w)\\+(?=\\w)");

  /** 문장형 노이즈를 식별하기 위한 불용어 키워드 (표준 스킬 매핑 실패 시 버림) */
  private static final Set<String> NOISE_KEYWORDS =
      Set.of(
          "우대", "보유자", "전산업무", "담당자", "필수", "가능자", "경력자", "신입", "채용", "모집", "자격", "전공자", "학사이상",
          "석사이상", "인근거주자", "운전가능자", "경력무관", "유경험자", "상관없음");

  /** 기술이 아니라 직무를 가리키는 태그 */
  private static final Set<String> JOB_TITLES =
      Set.of(
          "백엔드/서버개발",
          "웹개발",
          "프론트엔드",
          "앱개발",
          "si개발",
          "퍼블리셔",
          "풀스택",
          "웹마스터",
          "qa/테스터",
          "유지보수",
          "기술지원",
          "솔루션",
          "데이터엔지니어",
          "데이터분석가",
          "se(시스템엔지니어)",
          "아키텍쳐",
          "게임개발",
          "웹기획");

  private final SkillRepository skillRepository;
  private final SkillAliasRepository skillAliasRepository;

  /** DB 기반 동적 인메모리 캐시 맵 */
  private final Map<String, String> aliasMap = new ConcurrentHashMap<>();

  /** Aho-Corasick 고속 다중 키워드 매칭 Trie */
  private volatile Trie trie;

  public SkillTaxonomy() {
    this(null, null);
  }

  @Autowired
  public SkillTaxonomy(
      @Autowired(required = false) SkillRepository skillRepository,
      @Autowired(required = false) SkillAliasRepository skillAliasRepository) {
    this.skillRepository = skillRepository;
    this.skillAliasRepository = skillAliasRepository;
    reloadTaxonomy();
  }

  /** DB의 core.skill 및 core.skill_alias 테이블로부터 별칭 사전을 메모리로 로딩/갱신한다. */
  @PostConstruct
  public final void reloadTaxonomy() {
    Map<String, String> loaded = new HashMap<>();

    if (skillRepository != null && skillAliasRepository != null) {
      try {
        // 1. 표준 스킬 로딩 (s.standardName -> s.standardName)
        List<Skill> skills = skillRepository.findAll();
        for (Skill s : skills) {
          if (!s.isCustom()) {
            loaded.put(key(s.getStandardName()), s.getStandardName());
          }
        }

        // 2. 별칭 로딩 (alias.aliasName -> alias.skill.standardName)
        List<SkillAlias> aliases = skillAliasRepository.findAllWithSkill();
        for (SkillAlias a : aliases) {
          if (a.getSkill() != null) {
            loaded.put(key(a.getAliasName()), a.getSkill().getStandardName());
          }
        }

        log.info(
            "[SkillTaxonomy] DB로부터 스킬 {}건, 별칭 {}건(총 매핑 {}개)을 로드하여 캐시를 구축했습니다.",
            skills.size(),
            aliases.size(),
            loaded.size());
      } catch (Exception e) {
        log.warn("[SkillTaxonomy] DB 로딩 실패, 기본 fallback 사전을 사용합니다. 사유: {}", e.getMessage());
      }
    }

    // DB가 비어있거나 Repository가 없는 경우(단위테스트 등) 기본 사전 보충
    if (loaded.isEmpty()) {
      loaded.putAll(createFallbackAliasMap());
    }

    this.aliasMap.clear();
    this.aliasMap.putAll(loaded);

    // Aho-Corasick Trie 오토마타 빌드 (2글자 이상 키워드 등록)
    Trie.TrieBuilder builder = Trie.builder().ignoreOverlaps();
    for (String aliasKey : loaded.keySet()) {
      if (aliasKey.length() >= 2) {
        builder.addKeyword(aliasKey);
      }
    }
    this.trie = builder.build();
  }

  /**
   * 공고 제목이나 본문 같은 자연어 문장에서 사전에 등록된 표준 스킬만 단어 경계를 준수하여 고속 추출합니다. 사전에 없는 일반 단어나 노이즈는 절대 스킬로 승격되지
   * 않습니다.
   */
  public List<String> extractSkillsFromSentence(String sentence) {
    if (sentence == null || sentence.isBlank()) {
      return Collections.emptyList();
    }

    Trie currentTrie = this.trie;
    if (currentTrie == null) {
      return Collections.emptyList();
    }

    String normalizedText = key(sentence);
    Collection<Emit> emits = currentTrie.parseText(normalizedText);
    if (emits.isEmpty()) {
      return Collections.emptyList();
    }

    Set<String> results = new LinkedHashSet<>();
    for (Emit emit : emits) {
      if (isWordBoundary(normalizedText, emit.getStart(), emit.getEnd())) {
        String canonicalSkill = aliasMap.get(emit.getKeyword());
        if (canonicalSkill != null
            && !canonicalSkill.isBlank()
            && !JOB_TITLES.contains(key(canonicalSkill))) {
          results.add(canonicalSkill);
        }
      }
    }

    return new ArrayList<>(results);
  }

  private static final Pattern UNKNOWN_TECH_PATTERN = Pattern.compile("^[a-zA-Z0-9_+#.-]{2,20}$");

  /** 원천 태그에서 사전에 있는 표준 스킬만 고른다. 사전에 없는 값은 버린다(스킬 수요 분모를 흐리지 않게). */
  public List<String> matchKnownSkills(String rawText) {
    return extract(rawText, false);
  }

  public List<String> extractCanonicalSkills(String rawText) {
    return extract(rawText, true);
  }

  private List<String> extract(String rawText, boolean admitUnknownTech) {
    if (rawText == null || rawText.isBlank()) {
      return Collections.emptyList();
    }

    Set<String> results = new LinkedHashSet<>();
    String[] tokens = DELIMITER_PATTERN.split(rawText);

    for (String token : tokens) {
      if (token == null) continue;
      String trimmed = token.strip();
      if (trimmed.isEmpty() || trimmed.length() > 30) {
        continue;
      }

      String normalizedKey = key(trimmed);

      // 1. 별칭/표준 사전에서 직접 매칭
      if (aliasMap.containsKey(normalizedKey)) {
        results.add(aliasMap.get(normalizedKey));
        continue;
      }

      // 2. 문장형 잡음 키워드가 섞여 있으면 스킬이 아니므로 버림
      if (containsNoise(trimmed)) {
        continue;
      }

      // 3. Aho-Corasick Trie를 사용한 1-Pass 고속 다중 키워드 추출 (예: "Kotlin Spring Boot", "Java Python")
      Trie currentTrie = this.trie;
      Collection<Emit> emits =
          currentTrie != null ? currentTrie.parseText(normalizedKey) : Collections.emptyList();

      boolean matched = false;
      if (!emits.isEmpty()) {
        for (Emit emit : emits) {
          if (isWordBoundary(normalizedKey, emit.getStart(), emit.getEnd())) {
            String canonicalSkill = aliasMap.get(emit.getKeyword());
            if (canonicalSkill != null) {
              results.add(canonicalSkill);
              matched = true;
            }
          }
        }
      }

      // 4. 사전에 없는 단어: 1글자이거나 한글 일반명사(사전 미등록)는 노이즈이므로 버림.
      // 2~20자의 영문/기술 식별자 패턴인 경우에만 신기술 후보로 채택.
      if (admitUnknownTech
          && !matched
          && !JOB_TITLES.contains(normalizedKey)
          && UNKNOWN_TECH_PATTERN.matcher(trimmed).matches()) {
        results.add(trimmed);
      }
    }

    return new ArrayList<>(results);
  }

  /**
   * 텍스트 내에서 매칭된 토큰의 시작과 끝이 단어 경계(비알파벳/비숫자)인지 검사합니다. "javascript" 내의 "java" 매칭이나 "good" 내의 "go" 같은
   * 서브스트링 오탐을 방지합니다.
   */
  private static boolean isWordBoundary(String text, int start, int end) {
    if (start > 0) {
      char prev = text.charAt(start - 1);
      if (Character.isLetterOrDigit(prev)) {
        return false;
      }
    }
    if (end < text.length() - 1) {
      char next = text.charAt(end + 1);
      if (Character.isLetterOrDigit(next)) {
        return false;
      }
    }
    return true;
  }

  private static int findWholeWordIndex(String text, String word) {
    int idx = text.indexOf(word);
    while (idx != -1) {
      boolean leftBoundary = (idx == 0) || !Character.isLetterOrDigit(text.charAt(idx - 1));
      boolean rightBoundary =
          (idx + word.length() == text.length())
              || !Character.isLetterOrDigit(text.charAt(idx + word.length()));
      if (leftBoundary && rightBoundary) {
        return idx;
      }
      idx = text.indexOf(word, idx + 1);
    }
    return -1;
  }

  /** 단일 문자열 정규화 (하위 호환성 유지) */
  public String canonicalName(String rawName) {
    if (rawName == null || rawName.isBlank()) {
      return null;
    }
    List<String> extracted = extractCanonicalSkills(rawName);
    if (!extracted.isEmpty()) {
      return extracted.get(0);
    }
    String trimmed = rawName.strip();
    if (containsNoise(trimmed) || trimmed.length() > 30) {
      return null;
    }
    return aliasMap.getOrDefault(key(trimmed), trimmed);
  }

  public String category(String rawName) {
    if (rawName == null) return SKILL;
    return JOB_TITLES.contains(key(rawName)) ? JOB_CATEGORY : SKILL;
  }

  private static boolean containsNoise(String text) {
    for (String noise : NOISE_KEYWORDS) {
      if (text.contains(noise)) {
        return true;
      }
    }
    return false;
  }

  private static String key(String name) {
    return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
  }

  private static Map<String, String> createFallbackAliasMap() {
    Map<String, String> map = new HashMap<>();

    // Mobile
    add(map, "Android", "android", "안드로이드");
    add(map, "iOS", "ios", "아이폰");

    // Java
    add(map, "Java", "java", "java8", "java11", "java17", "java21", "jdk", "자바", "java mdd");
    // Spring Boot & Framework
    add(
        map,
        "Spring Boot",
        "spring boot",
        "springboot",
        "spring",
        "spring framework",
        "스프링",
        "스프링부트",
        "spring batch",
        "egovframe",
        "전자정부프레임워크",
        "egov");
    // JavaScript
    add(
        map,
        "JavaScript",
        "javascript",
        "javascrip",
        "x-javascript",
        "js",
        "자바스크립트",
        "vanilla js",
        "ecmascript");
    // TypeScript
    add(map, "TypeScript", "typescript", "ts", "타입스크립트");
    // Python
    add(map, "Python", "python", "python3", "파이썬", "py", "fastapi", "django", "flask");
    // Kotlin
    add(map, "Kotlin", "kotlin", "코틀린");
    // Go
    add(map, "Go", "go", "golang", "고랭");
    // C# & .NET
    add(map, "C#", "c#", "csharp", ".net", "dotnet", "asp.net", "asp");
    // C / C++
    add(map, "C++", "c++", "cpp");
    add(map, "C", "c언어", "c language");
    // PHP
    add(map, "PHP", "php", "laravel");
    // Ruby
    add(map, "Ruby", "ruby", "rails", "ruby on rails");
    // Swift & Flutter
    add(map, "Swift", "swift", "스위프트", "ios");
    add(map, "Flutter", "flutter", "플러터", "dart");
    // Frontend Web
    add(map, "React", "react", "react.js", "reactjs", "리액트", "react native");
    add(map, "Next.js", "next.js", "nextjs", "next", "넥스트");
    add(map, "Vue.js", "vue", "vue.js", "vuejs", "뷰");
    add(map, "Node.js", "node.js", "nodejs", "node", "노드", "express", "nest.js", "nestjs");
    add(map, "HTML", "html", "html5");
    add(map, "CSS", "css", "css3");
    add(map, "jQuery", "jquery", "제이쿼리");
    add(map, "JSP", "jsp");
    add(map, "Tailwind CSS", "tailwind", "tailwindcss");
    // DB & Storage
    add(map, "PostgreSQL", "postgresql", "postgres", "포스트그레스");
    add(map, "MySQL", "mysql", "마이에스큐엘", "mariadb");
    add(map, "Oracle", "oracle", "오라클", "oracle db");
    add(map, "MSSQL", "mssql", "sql server", "sqlserver");
    add(map, "Redis", "redis", "레디스");
    add(map, "MongoDB", "mongodb", "mongo", "몽고db");
    add(map, "SQL", "sql", "rdbms", "ansi-sql");
    // Infra & Cloud & DevOps
    add(map, "Docker", "docker", "도커");
    add(map, "Kubernetes", "kubernetes", "k8s", "쿠버네티스");
    add(map, "AWS", "aws", "amazon web services", "아마존웹서비스");
    add(map, "GCP", "gcp", "google cloud");
    add(map, "Azure", "azure");
    add(map, "Linux", "linux", "리눅스", "ubuntu", "centos");
    add(map, "Git", "git", "github", "gitlab", "깃");
    add(map, "Kafka", "kafka", "카프카");
    add(map, "RabbitMQ", "rabbitmq");
    add(map, "Elasticsearch", "elasticsearch", "elastic search", "엘라스틱서치");
    add(map, "Apache Airflow", "apache airflow", "airflow", "에어플로우");
    add(map, "Apache Spark", "apache spark", "spark", "스파크");
    add(map, "Hadoop", "hadoop", "하둡");
    // AI / ML
    add(map, "PyTorch", "pytorch", "파이토치");
    add(map, "TensorFlow", "tensorflow", "텐서플로우");
    add(map, "LLM", "llm", "langchain", "rag", "openai");
    // Framework / Architecture
    add(map, "JPA", "jpa", "hibernate", "하이버네이트", "spring data jpa");
    add(map, "MyBatis", "mybatis", "마이바티스");
    add(map, "REST API", "rest api", "restful", "rest");
    add(map, "GraphQL", "graphql");

    return map;
  }

  private static void add(Map<String, String> map, String standardName, String... aliases) {
    map.put(key(standardName), standardName);
    for (String alias : aliases) {
      map.put(key(alias), standardName);
    }
  }
}
