package fr.an.projectanalysis.github.rest.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class YearCountDTO {

    public int year;
    public int count;
    /** Lowest/highest id in the partition (ids are sequential), or null when the partition is empty. */
    public Integer minId;
    public Integer maxId;

}
