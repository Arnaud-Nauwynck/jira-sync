package fr.an.projectanalysis.jira.rest.dtos;

import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class IssuesCountsDTO {

    public int total;
    public int totalLive;
    public List<IssuesCountByKeyDTO> countByKeys;

    @AllArgsConstructor
    public static class IssuesCountByKeyDTO {
        /** key fields, example: [resolution,type,component] */
        public Object[] key;
        public int count;
    }

}
