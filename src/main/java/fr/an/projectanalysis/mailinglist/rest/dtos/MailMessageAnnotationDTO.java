package fr.an.projectanalysis.mailinglist.rest.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor @AllArgsConstructor
public class MailMessageAnnotationDTO {

    public String messageId;
    public MailMessageExtraFieldsDTO annotated;

}
