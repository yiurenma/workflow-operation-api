package com.workflow.dao.repository;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.envers.Audited;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
        name = "WORKFLOW_ENTITY_AND_LINKING_ID_MAPPING",
        indexes = {
                @Index(name = "idx_entity_linking_entity_logic", columnList = "workflow_entity_setting_id,logic_order"),
                @Index(name = "idx_entity_linking_rule_type_mapping", columnList = "workflow_rule_and_type_mapping_id"),
                @Index(name = "idx_entity_linking_legacy_linking_id", columnList = "workflow_rule_and_type_linking_id")
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
public class WorkflowEntityAndLinkingIdMapping extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(name = "workflow_rule_and_type_linking_id")
    private String workflowRuleAndTypeLinkingId;

    @Column(name = "workflow_rule_and_type_mapping_id")
    private Long workflowRuleAndTypeMappingId;

    @ManyToOne
    @JoinColumn(
            name = "workflow_entity_setting_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_entity_linking_entity_setting")
    )
    private WorkflowEntitySetting workflowEntitySetting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "workflow_rule_and_type_mapping_id",
            referencedColumnName = "id",
            foreignKey = @ForeignKey(name = "fk_entity_linking_rule_type_mapping"),
            insertable = false,
            updatable = false
    )
    private WorkflowRuleAndType workflowRuleAndTypeMapping;

    @Column(name = "logic_order")
    private Integer logicOrder;
    private String remark;
}
