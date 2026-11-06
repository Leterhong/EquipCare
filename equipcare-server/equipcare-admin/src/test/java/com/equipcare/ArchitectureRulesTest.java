package com.equipcare;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构守护测试（第八期创新升级）：把多模块依赖方向固化为可执行规则。
 *
 * <pre>
 *   common ← system ← framework
 *   common ← system ← business（含 ai 同层）
 *   admin → 全部
 * </pre>
 *
 * <p>已知技术债（有意白名单，勿“修复”规则来掩盖）：
 * workorder ↔ inventory 因工单领用备件流程存在包级互相引用（SpWorkOrderPartMapper
 * 与 WoWorkOrderMapper），二者保留在同一 business 模块内，待引入服务接口反转后解除。
 */
class ArchitectureRulesTest {

    private static final String BUSINESS_PACKAGES = "com.equipcare.(asset|workorder|inspection|inventory|analytics|export|notification|ai)..";

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.equipcare");

    @Test
    void 业务模块不得依赖框架层() {
        // framework 所拥有的包：outbox / realtime / security / config / audit
        noClasses().that().resideInAPackage(BUSINESS_PACKAGES)
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.equipcare.framework..",
                        "com.equipcare.security..",
                        "com.equipcare.config..",
                        "com.equipcare.outbox..",
                        "com.equipcare.realtime..")
                .because("业务模块只依赖 common（接口）与 system，横切设施通过 common 的接口反转访问")
                .check(classes);
    }

    @Test
    void common不得反向依赖任何上层模块() {
        noClasses().that().resideInAPackage("com.equipcare.common..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.equipcare.system..",
                        "com.equipcare.framework..",
                        "com.equipcare.security..",
                        "com.equipcare.config..",
                        "com.equipcare.outbox..",
                        "com.equipcare.realtime..",
                        "com.equipcare.asset..",
                        "com.equipcare.workorder..",
                        "com.equipcare.inspection..",
                        "com.equipcare.inventory..",
                        "com.equipcare.analytics..",
                        "com.equipcare.export..",
                        "com.equipcare.notification..",
                        "com.equipcare.ai..")
                .because("common 是依赖图的根，反向依赖会让分层彻底失效")
                .check(classes);
    }

    @Test
    void 事件信封的消费只落在notification模块() {
        // OutboxEventArrived 信封（事件消费入口）只允许 notification 桥接监听器依赖；
        // 业务模块通过 DomainEventPublisher 发布属于正常用法，不受此限
        noClasses().that().resideInAPackage(BUSINESS_PACKAGES)
                .and().resideOutsideOfPackage("com.equipcare.notification..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.equipcare.common.event.OutboxEventArrived")
                .because("领域事件信封的消费统一收口在 notification 模块的桥接监听器")
                .check(classes);
    }
}
