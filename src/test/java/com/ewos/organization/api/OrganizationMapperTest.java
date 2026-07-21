package com.ewos.organization.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.organization.api.dto.OrganizationUnitResponse;
import com.ewos.organization.api.dto.OrganizationUnitTreeNode;
import com.ewos.organization.api.dto.OrganizationUnitTypeResponse;
import com.ewos.organization.domain.OrganizationUnit;
import com.ewos.organization.domain.OrganizationUnitStatus;
import com.ewos.organization.domain.OrganizationUnitType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizationMapperTest {

    private final OrganizationMapper mapper = new OrganizationMapper();

    @Test
    void unitTypeMapsAllFields() {
        UUID typeId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        OrganizationUnitType t = new OrganizationUnitType();
        t.setId(typeId);
        t.setTenantId(tenantId);
        t.setCode("DEPT");
        t.setName("Department");
        t.setDescription("Standard department");
        t.setSortOrder(10);
        t.setActive(true);

        OrganizationUnitTypeResponse r = mapper.toResponse(t);

        assertThat(r.id()).isEqualTo(typeId);
        assertThat(r.tenantId()).isEqualTo(tenantId);
        assertThat(r.code()).isEqualTo("DEPT");
        assertThat(r.name()).isEqualTo("Department");
        assertThat(r.description()).isEqualTo("Standard department");
        assertThat(r.sortOrder()).isEqualTo(10);
        assertThat(r.active()).isTrue();
    }

    @Test
    void unitMapsAllFieldsIncludingParent() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        OrganizationUnitType type = new OrganizationUnitType();
        type.setId(UUID.randomUUID());
        type.setCode("DIV");

        OrganizationUnit parent = new OrganizationUnit();
        parent.setId(UUID.randomUUID());

        OrganizationUnit unit = new OrganizationUnit();
        unit.setId(UUID.randomUUID());
        unit.setTenantId(tenantId);
        unit.setCompanyId(companyId);
        unit.setUnitType(type);
        unit.setParent(parent);
        unit.setCode("EMEA");
        unit.setName("EMEA Division");
        unit.setDescription("Europe/Middle East/Africa");
        unit.setCountryCode("DE");
        unit.setCostCenterCode("CC-EMEA-01");
        unit.setManagerPersonId(UUID.randomUUID());
        unit.setStatus(OrganizationUnitStatus.ACTIVE);
        unit.setEffectiveFrom(LocalDate.of(2024, 1, 1));

        OrganizationUnitResponse r = mapper.toResponse(unit);

        assertThat(r.tenantId()).isEqualTo(tenantId);
        assertThat(r.companyId()).isEqualTo(companyId);
        assertThat(r.unitTypeId()).isEqualTo(type.getId());
        assertThat(r.unitTypeCode()).isEqualTo("DIV");
        assertThat(r.parentId()).isEqualTo(parent.getId());
        assertThat(r.code()).isEqualTo("EMEA");
        assertThat(r.countryCode()).isEqualTo("DE");
        assertThat(r.costCenterCode()).isEqualTo("CC-EMEA-01");
        assertThat(r.managerPersonId()).isEqualTo(unit.getManagerPersonId());
        assertThat(r.status()).isEqualTo(OrganizationUnitStatus.ACTIVE);
        assertThat(r.effectiveFrom()).isEqualTo(LocalDate.of(2024, 1, 1));
    }

    @Test
    void treeBuildsHierarchyFromFlatList() {
        OrganizationUnit root = unit("ROOT", null);
        OrganizationUnit c1 = unit("C1", root);
        OrganizationUnit c2 = unit("C2", root);
        OrganizationUnit g1 = unit("G1", c1);

        List<OrganizationUnitTreeNode> tree = mapper.toTree(List.of(root, c1, c2, g1));

        assertThat(tree).hasSize(1);
        OrganizationUnitTreeNode rootNode = tree.get(0);
        assertThat(rootNode.code()).isEqualTo("ROOT");
        assertThat(rootNode.children()).hasSize(2).extracting("code").containsExactly("C1", "C2");
        assertThat(rootNode.children().get(0).children())
                .hasSize(1)
                .extracting("code")
                .containsExactly("G1");
    }

    @Test
    void treePromotesOrphansToRoot() {
        // Parent not in list ⇒ its child becomes a root, not silently dropped.
        OrganizationUnit ghost = unit("GHOST", null);
        OrganizationUnit orphan = unit("ORPHAN", ghost);

        List<OrganizationUnitTreeNode> tree = mapper.toTree(List.of(orphan));

        assertThat(tree).hasSize(1).extracting("code").containsExactly("ORPHAN");
    }

    private static OrganizationUnit unit(String code, OrganizationUnit parent) {
        OrganizationUnit u = new OrganizationUnit();
        u.setId(UUID.randomUUID());
        u.setTenantId(UUID.randomUUID());
        u.setCompanyId(UUID.randomUUID());
        u.setCode(code);
        u.setName(code);
        u.setStatus(OrganizationUnitStatus.ACTIVE);
        u.setEffectiveFrom(LocalDate.now());
        u.setParent(parent);
        return u;
    }
}
