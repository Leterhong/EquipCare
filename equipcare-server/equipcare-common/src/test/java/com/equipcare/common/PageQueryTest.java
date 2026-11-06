package com.equipcare.common;

import com.equipcare.common.query.PageQuery;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分页参数单元测试：重点验证排序列白名单，防止 SQL 注入。
 */
class PageQueryTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("默认分页参数为第 1 页、每页 10 条")
    void defaults() {
        PageQuery query = new PageQuery();
        assertThat(query.getPageNum()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(10);
        assertThat(query.offset()).isZero();
        assertThat(query.isAsc()).isTrue();
    }

    @Test
    @DisplayName("offset 按页码正确计算")
    void offset() {
        PageQuery query = new PageQuery();
        query.setPageNum(3);
        query.setPageSize(20);
        assertThat(query.offset()).isEqualTo(40L);
    }

    @Test
    @DisplayName("合法排序列通过，非法排序列被丢弃")
    void safeOrderBy() {
        PageQuery query = new PageQuery();
        query.setOrderBy("create_time");
        assertThat(query.safeOrderBy()).isEqualTo("create_time");

        query.setOrderBy("id");
        assertThat(query.safeOrderBy()).isEqualTo("id");

        // 注入尝试
        query.setOrderBy("id; DROP TABLE sys_user");
        assertThat(query.safeOrderBy()).isNull();

        query.setOrderBy("id)--");
        assertThat(query.safeOrderBy()).isNull();

        query.setOrderBy("name desc");
        assertThat(query.safeOrderBy()).isNull();

        query.setOrderBy(null);
        assertThat(query.safeOrderBy()).isNull();

        query.setOrderBy("   ");
        assertThat(query.safeOrderBy()).isNull();
    }

    @Test
    @DisplayName("排序方向只认 desc，其余按升序处理")
    void isAsc() {
        PageQuery query = new PageQuery();
        query.setOrderDir("DESC");
        assertThat(query.isAsc()).isFalse();
        query.setOrderDir("asc");
        assertThat(query.isAsc()).isTrue();
        query.setOrderDir("unknown");
        assertThat(query.isAsc()).isTrue();
    }

    @Test
    @DisplayName("pageSize 超过 100 或页码小于 1 时校验失败")
    void validation() {
        PageQuery query = new PageQuery();
        query.setPageSize(500);
        Set<ConstraintViolation<PageQuery>> violations = validator.validate(query);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("100");

        query.setPageSize(20);
        query.setPageNum(0);
        violations = validator.validate(query);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("页码");
    }
}
