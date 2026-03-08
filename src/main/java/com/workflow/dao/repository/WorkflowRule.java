package com.workflow.dao.repository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.envers.Audited;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
        name = "WORKFLOW_RULE",
        indexes = {
                @Index(name = "idx_workflow_rule_rule_key", columnList = "rule_key")
        }
)
@DynamicUpdate
@Audited
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Setter
@Getter
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class WorkflowRule extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("Primary key, auto-generated")
    private Long id;

    @Column(name = "rule_key", nullable = false)
    @Comment("JSONPath expression to validate runtime data (evaluates to true/false). Uses Jayway JsonPath syntax.")
    private String key;

    @Column(name = "remark")
    @Comment("Free-text description or notes for the rule")
    private String remark;
}
