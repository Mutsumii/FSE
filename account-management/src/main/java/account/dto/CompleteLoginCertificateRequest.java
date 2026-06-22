package account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompleteLoginCertificateRequest {

    @NotBlank
    @JsonProperty("subject_type")
    private String subjectType;

    @NotBlank
    @JsonProperty("subject_key")
    private String subjectKey;

    @NotBlank
    @JsonProperty("certificate_code")
    private String certificateCode;
}
