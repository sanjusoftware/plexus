package com.bankengine.catalog.specification;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.catalog.dto.ProductSearchRequest;
import com.bankengine.catalog.model.Product;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductSpecificationTest {

    @Mock
    private Root<Product> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder builder;

    @Mock
    private Predicate predicate;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setBankId("BANK_A");
        lenient().when(builder.equal(any(), any())).thenReturn(predicate);
        lenient().when(builder.like(any(), anyString())).thenReturn(predicate);
        lenient().when(builder.lower(any())).thenReturn(mock(Expression.class));
        lenient().when(builder.greaterThanOrEqualTo(any(), any(LocalDate.class))).thenReturn(predicate);
        lenient().when(builder.lessThanOrEqualTo(any(), any(LocalDate.class))).thenReturn(predicate);
        lenient().when(builder.and(any())).thenReturn(predicate);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void testFilterBy_AllFilters() {
        ProductSearchRequest request = new ProductSearchRequest();
        request.setName("Test");
        request.setStatus("ACTIVE");
        request.setCategory("RETAIL");
        request.setProductTypeId(1L);
        request.setEffectiveDateFrom(LocalDate.now());
        request.setEffectiveDateTo(LocalDate.now());

        Path bankIdPath = mock(Path.class);
        Path namePath = mock(Path.class);
        Path statusPath = mock(Path.class);
        Path categoryPath = mock(Path.class);
        Path productTypePath = mock(Path.class);
        Path productTypeIdPath = mock(Path.class);
        Path effectiveDatePath = mock(Path.class);

        when(root.get("bankId")).thenReturn(bankIdPath);
        when(root.get("name")).thenReturn(namePath);
        when(root.get("status")).thenReturn(statusPath);
        when(root.get("category")).thenReturn(categoryPath);
        when(root.get("productType")).thenReturn(productTypePath);
        when(productTypePath.get("id")).thenReturn(productTypeIdPath);
        when(root.get("effectiveDate")).thenReturn(effectiveDatePath);

        Specification<Product> spec = ProductSpecification.filterBy(request);
        spec.toPredicate(root, query, builder);

        verify(builder).equal(bankIdPath, "BANK_A");
        verify(builder).like(any(), eq("%test%"));
        verify(builder).equal(statusPath, "ACTIVE");
        verify(builder).equal(categoryPath, "RETAIL");
        verify(builder).equal(productTypeIdPath, 1L);
        verify(builder).greaterThanOrEqualTo(eq(effectiveDatePath), any(LocalDate.class));
        verify(builder).lessThanOrEqualTo(eq(effectiveDatePath), any(LocalDate.class));
    }

    @Test
    void testFilterBy_EmptyFilters() {
        ProductSearchRequest request = new ProductSearchRequest();
        Path bankIdPath = mock(Path.class, "bankIdPath");
        when(root.get("bankId")).thenReturn(bankIdPath);

        Specification<Product> spec = ProductSpecification.filterBy(request);
        spec.toPredicate(root, query, builder);
        verify(builder).equal(any(), eq("BANK_A"));
    }
}
