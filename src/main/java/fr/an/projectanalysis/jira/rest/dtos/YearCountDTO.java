package fr.an.projectanalysis.jira.rest.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class YearCountDTO {

    public int year;
    public int count;
    /** Lowest/highest numeric suffix of the issue key in the partition (eg "123" in "PROJ-123"), or null when
     * the partition is empty or no key could be parsed. */
    public Integer minId;
    public Integer maxId;

}
