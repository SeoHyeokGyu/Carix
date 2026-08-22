package com.carix.authservice.magiclink;

/** 실제 이메일 발송 provider 는 아직 정해지지 않았다(설계 문서 참고). 이 인터페이스는 provider 를 나중에 갈아끼울 수 있도록 발송 로직을 분리한다. */
public interface MagicLinkNotifier {

  void send(String email, String rawToken);
}
