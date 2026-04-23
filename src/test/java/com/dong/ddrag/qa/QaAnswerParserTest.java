package com.dong.ddrag.qa;

import com.dong.ddrag.qa.model.KnowledgeAnswerOutput;
import com.dong.ddrag.qa.support.QaAnswerParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QaAnswerParserTest {

    @Test
    void shouldParseStructuredAnswer() {
        QaAnswerParser parser = new QaAnswerParser(new ObjectMapper());
        String json = """
                {"answered":true,"answer":"答案","reasonCode":null,"reasonMessage":null}
                """;

        KnowledgeAnswerOutput output = parser.parse(json);

        assertThat(output).isNotNull();
        assertThat(output.answered()).isTrue();
        assertThat(output.answer()).isEqualTo("答案");
        assertThat(output.reasonCode()).isNull();
    }

    @Test
    void shouldReturnNullForInvalidAnswer() {
        QaAnswerParser parser = new QaAnswerParser(new ObjectMapper());

        KnowledgeAnswerOutput output = parser.parse("not-json");

        assertThat(output).isNull();
    }
}
