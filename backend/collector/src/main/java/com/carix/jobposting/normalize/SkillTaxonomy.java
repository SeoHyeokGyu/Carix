package com.carix.jobposting.normalize;

import com.carix.jobposting.Skill;
import com.carix.jobposting.SkillAlias;
import com.carix.jobposting.SkillAliasRepository;
import com.carix.jobposting.SkillRepository;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * DB (core.skill 및 core.skill_alias) 기반으로 스킬 별칭 사전을 동적으로 로딩하여 복합 토큰 분리, 표준 표기 통일, 잡음 필터링을 수행하는 스킬
 * 정규화 엔진.
 */
@Slf4j
@Component
public class SkillTaxonomy {

  public static final String SKILL = "SKILL";
  public static final String JOB_CATEGORY = "JOB_CATEGORY";

  /** 복합 문자열 분리용 정규식 (쉼표, 슬래시, 역슬래시, 세미콜론, 줄바꿈, 파이프, 앰퍼샌드, 괄호, 가운뎃점, 마침표 등) */
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
  public void reloadTaxonomy() {
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
  }

  public List<String> extractCanonicalSkills(String rawText) {
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

      // 3. 공백이나 점 등으로 여러 스킬이 붙어있는 경우 (예: "Kotlin Spring Boot", "Java Python")
      // 텍스트 내 등장 위치(start index) 순으로 정렬하여 추출
      record MatchSpan(int startIdx, int length, String skillName) {}
      List<MatchSpan> spans = new ArrayList<>();

      for (Map.Entry<String, String> entry : aliasMap.entrySet()) {
        String aliasKey = entry.getKey();
        if (aliasKey.length() >= 2) {
          int matchIdx = findWholeWordIndex(normalizedKey, aliasKey);
          if (matchIdx != -1) {
            spans.add(new MatchSpan(matchIdx, aliasKey.length(), entry.getValue()));
          }
        }
      }

      if (!spans.isEmpty()) {
        // 더 긴 단어 우선 + 등장 위치 순 정렬
        spans.sort(
            (a, b) -> {
              int cmp = Integer.compare(a.startIdx, b.startIdx);
              return cmp != 0 ? cmp : Integer.compare(b.length, a.length);
            });

        for (MatchSpan span : spans) {
          results.add(span.skillName());
        }
      } else if (!JOB_TITLES.contains(normalizedKey) && trimmed.length() <= 20) {
        results.add(trimmed);
      }
    }

    return new ArrayList<>(results);
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
        "spring batch");
    // JavaScript
    add(map, "JavaScript", "javascript", "javascrip", "x-javascript", "js", "자바스크립트", "vanilla js");
    // TypeScript
    add(map, "TypeScript", "typescript", "ts", "타입스크립트");
    // Python
    add(map, "Python", "python", "python3", "파이썬", "py");
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
