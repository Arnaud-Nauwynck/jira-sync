package fr.an.jira.rest.dtos;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor @AllArgsConstructor
public class PersonalInterrestCommentDTO {

    public String key;
    public String personalInterrestComment;
    public Integer personalInterrestPriority10;

}
