package mil.tron.commonapi.dto.documentspace;

import lombok.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Used for sending search query to document space
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DocumentSpaceSearchDto {

    @Getter
    @Setter
    @NotNull
    @NotBlank
    private String query;
}
