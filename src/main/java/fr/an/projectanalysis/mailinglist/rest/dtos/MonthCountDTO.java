package fr.an.projectanalysis.mailinglist.rest.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthCountDTO {

    /** 'yyyy-MM'. */
    public String month;
    public int count;

}
