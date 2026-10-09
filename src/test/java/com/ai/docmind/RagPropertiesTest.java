package com.ai.docmind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ai.docmind.config.RagProperties;
import org.junit.jupiter.api.Test;

class RagPropertiesTest {

    @Test
    void defaultsAreValid() {
        var props = new RagProperties(500, 100, 5, 0.7);
        assertThat(props.chunkSize()).isEqualTo(500);
        assertThat(props.topK()).isEqualTo(5);
    }

    @Test
    void rejectsBadValues() {
        assertThatThrownBy(() -> new RagProperties(0, 0, 5, 0.7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RagProperties(500, 500, 5, 0.7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RagProperties(500, 100, 0, 0.7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RagProperties(500, 100, 5, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
