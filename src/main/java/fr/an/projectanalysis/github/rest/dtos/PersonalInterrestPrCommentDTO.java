package fr.an.projectanalysis.github.rest.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor @AllArgsConstructor
public class PersonalInterrestPrCommentDTO {

    public int number;
    public String personalInterrestComment;
    public Integer personalInterrestPriority10;

}
