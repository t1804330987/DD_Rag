package com.dong.ddrag.common;

import com.dong.ddrag.common.api.ApiResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void shouldBuildSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success("ok");
        assertThat(response.getData()).isEqualTo("ok");
    }
}
