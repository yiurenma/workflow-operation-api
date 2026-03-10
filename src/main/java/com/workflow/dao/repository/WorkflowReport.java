package com.workflow.dao.repository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;

@Entity
@Table(name = "WORKFLOW_REPORT")
@DynamicUpdate
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Setter
@Getter
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class WorkflowReport extends Auditable implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(nullable = false)
    private Long reportGroup;
    @Column(nullable = false)
    private boolean enabled;

    @ManyToOne
    @JoinColumn(name = "workflow_entity_setting_id",nullable = false)
    WorkflowEntitySetting workflowEntitySetting;

    @Column(nullable = false)
    private String cronExpression;
    @Column(nullable = false)
    private Integer reportTimeRangeByHours;
    //refer to https://www.iplocate.com/en/resources/timezones/timezone-profile/timezone/197
    @Column(name = "TIMEZONE", nullable = false)
    private String timezone;
    private String emailTitle;
    @Column(columnDefinition = "TEXT")
    private String emailBody;
    private String emailAttachmentFileName;
    private String emailReceiptAddressList;
    @Column(columnDefinition = "TEXT")
    private String emailAttachmentContent;
    private String password;
}
