package org.ddamme.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class FileUtilsTest {

    @Test
    @DisplayName("getContentTypeOrDefault returns default for null input")
    void getContentTypeOrDefault_nullInput() {
        assertThat(FileUtils.getContentTypeOrDefault(null)).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("getContentTypeOrDefault returns default for blank input")
    void getContentTypeOrDefault_blankInput() {
        assertThat(FileUtils.getContentTypeOrDefault("")).isEqualTo("application/octet-stream");
        assertThat(FileUtils.getContentTypeOrDefault("   ")).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("getContentTypeOrDefault preserves valid content type")
    void getContentTypeOrDefault_validInput() {
        assertThat(FileUtils.getContentTypeOrDefault("text/plain")).isEqualTo("text/plain");
    }

    @Test
    @DisplayName("sanitizeFilename handles null and blank inputs")
    void sanitizeFilename_nullAndBlank() {
        assertThat(FileUtils.sanitizeFilename(null)).isEqualTo("file");
        assertThat(FileUtils.sanitizeFilename("")).isEqualTo("file");
        assertThat(FileUtils.sanitizeFilename("   ")).isEqualTo("file");
    }

    @Test
    @DisplayName("sanitizeFilename preserves safe characters")
    void sanitizeFilename_safeCharacters() {
        assertThat(FileUtils.sanitizeFilename("document-v1.2_final.pdf"))
                .isEqualTo("document-v1.2_final.pdf");
    }

    @Test
    @DisplayName("sanitizeFilename replaces unsafe characters")
    void sanitizeFilename_unsafeCharacters() {
        assertThat(FileUtils.sanitizeFilename("my file/with\\special:chars|<>?.pdf"))
                .isEqualTo("my_file_with_special_chars____.pdf");
    }

    @ParameterizedTest
    @CsvSource({
            "'résumé.pdf', 'r%C3%A9sum%C3%A9.pdf'",
            "'空 白.txt', '%E7%A9%BA%20%E7%99%BD.txt'",
            "'a\"b\\c.txt', 'a%22b%5Cc.txt'",
            "'hello world.doc', 'hello%20world.doc'",
            "'test+file.png', 'test%2Bfile.png'",
            "'файл.docx', '%D1%84%D0%B0%D0%B9%D0%BB.docx'"
    })
    @DisplayName("rfc5987Encode produces correct percent-encoding for international filenames")
    void rfc5987Encode_internationalFilenames(String input, String expected) {
        assertThat(FileUtils.rfc5987Encode(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("rfc5987Encode preserves unreserved characters")
    void rfc5987Encode_unreservedCharacters() {
        String unreserved = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~";
        assertThat(FileUtils.rfc5987Encode(unreserved)).isEqualTo(unreserved);
    }

    @Test
    @DisplayName("rfc5987Encode handles empty string")
    void rfc5987Encode_emptyString() {
        assertThat(FileUtils.rfc5987Encode("")).isEmpty();
    }
}
