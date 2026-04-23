package com.dong.ddrag.ingestion.parser.strategy;

import java.io.InputStream;

public interface DocumentParser {

    boolean supports(String extension);

    String parse(InputStream inputStream);
}
