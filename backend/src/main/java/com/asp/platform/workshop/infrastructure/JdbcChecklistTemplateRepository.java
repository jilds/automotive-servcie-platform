package com.asp.platform.workshop.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.asp.platform.shared.StaleDataException;
import com.asp.platform.shared.persistence.Constraints;
import com.asp.platform.workshop.domain.ChecklistKind;
import com.asp.platform.workshop.domain.ChecklistTemplate;
import com.asp.platform.workshop.domain.ChecklistTemplateRepository;
import com.asp.platform.workshop.domain.TemplateItem;

/// Plain SQL: saving replaces all items of a template, which an entity mapping would only complicate. Row-level
/// security limits every statement to the current workshop and `tenant_id` comes from the connection.
///
/// The stored version is 1 after the first save and grows by one per save; 0 stands for "never saved".
@Repository
class JdbcChecklistTemplateRepository implements ChecklistTemplateRepository {

    /// The unique `(tenant_id, kind)` key of V19.
    static final String TENANT_KIND_KEY = "checklist_template_tenant_kind_key";

    private final JdbcClient jdbc;

    JdbcChecklistTemplateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ChecklistTemplate> find(ChecklistKind kind) {
        return jdbc.sql("select id, version from checklist_template where kind = :kind")
                .param("kind", kind.name())
                .query((rs, _) -> new Header(rs.getObject("id", UUID.class), rs.getLong("version")))
                .optional()
                .map(header -> new ChecklistTemplate(kind, items(header.id()), true, header.version()));
    }

    /// A first save (version 0) inserts; any other version replaces the items, if the stored version is still that
    /// one.
    ///
    /// @throws StaleDataException when someone saved the template in between
    @Override
    public ChecklistTemplate save(ChecklistTemplate template) {
        long version = template.version();
        UUID id;
        if (version == 0) {
            id = UUID.randomUUID();
            try {
                jdbc.sql("""
                        insert into checklist_template (id, kind, version, created_at, updated_at)
                        values (:id, :kind, 1, now(), now())""")
                        .param("id", id)
                        .param("kind", template.kind().name())
                        .update();
            } catch (DataIntegrityViolationException e) {
                if (Constraints.violated(e, TENANT_KIND_KEY)) {
                    throw new StaleDataException();
                }
                throw e;
            }
        } else {
            id = jdbc.sql("""
                    update checklist_template set version = version + 1, updated_at = now()
                    where kind = :kind and version = :version
                    returning id""")
                    .param("kind", template.kind().name())
                    .param("version", version)
                    .query(UUID.class)
                    .optional()
                    .orElseThrow(StaleDataException::new);
            jdbc.sql("delete from checklist_template_item where template_id = :id").param("id", id).update();
        }
        int position = 1;
        for (TemplateItem item : template.items()) {
            jdbc.sql("""
                    insert into checklist_template_item (id, template_id, position, label, required)
                    values (:id, :template, :position, :label, :required)""")
                    .param("id", UUID.randomUUID())
                    .param("template", id)
                    .param("position", position++)
                    .param("label", item.label())
                    .param("required", item.required())
                    .update();
        }
        return new ChecklistTemplate(template.kind(), template.items(), true, version + 1);
    }

    private List<TemplateItem> items(UUID templateId) {
        return jdbc.sql("select label, required from checklist_template_item where template_id = :id "
                + "order by position")
                .param("id", templateId)
                .query((rs, _) -> new TemplateItem(rs.getString("label"), rs.getBoolean("required")))
                .list();
    }

    private record Header(UUID id, long version) {
    }
}
