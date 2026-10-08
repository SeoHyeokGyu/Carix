package com.carix.pipeline.health;

/** 점검 데이터를 한 번에 읽는다. 읽기 전용. */
public interface DataHealthReader {
  DataHealthSnapshot read();
}
