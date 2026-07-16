package com.envisione.progressiveskills.architecture;

import com.envisione.progressiveskills.ProjectIdentity;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureBoundaryTest {
    private static final String API_PACKAGE = ProjectIdentity.ROOT_PACKAGE + ".api..";
    private static final String COMMON_PACKAGE = ProjectIdentity.ROOT_PACKAGE + ".common..";
    private static final String SERVER_PACKAGE = ProjectIdentity.ROOT_PACKAGE + ".server..";
    private static final String CLIENT_PACKAGE = ProjectIdentity.ROOT_PACKAGE + ".client..";
    private static final String COMPAT_PACKAGE = ProjectIdentity.ROOT_PACKAGE + ".compat..";
    private static final String MIXIN_PACKAGE = ProjectIdentity.ROOT_PACKAGE + ".mixin..";

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(ProjectIdentity.ROOT_PACKAGE);
    }

    @Test
    void clientCodeDoesNotLeakAcrossThePhysicalSideBoundary() {
        noClasses()
                .that().resideOutsideOfPackage(CLIENT_PACKAGE)
                .should().dependOnClassesThat().resideInAnyPackage(
                        CLIENT_PACKAGE,
                        "net.minecraft.client..",
                        "com.mojang.blaze3d..",
                        "org.lwjgl.."
                )
                .because("common, server, API, and bootstrap code must load on a dedicated server")
                .check(productionClasses);
    }

    @Test
    void commonCodeDoesNotDependOnDirectionalOrOptionalImplementations() {
        noClasses()
                .that().resideInAPackage(COMMON_PACKAGE)
                .should().dependOnClassesThat().resideInAnyPackage(
                        CLIENT_PACKAGE,
                        SERVER_PACKAGE,
                        COMPAT_PACKAGE,
                        MIXIN_PACKAGE
                )
                .because("common code is the physical-side-neutral dependency root")
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    @Test
    void serverCodeDoesNotDependOnClientOrOptionalImplementations() {
        noClasses()
                .that().resideInAPackage(SERVER_PACKAGE)
                .should().dependOnClassesThat().resideInAnyPackage(
                        CLIENT_PACKAGE,
                        COMPAT_PACKAGE,
                        MIXIN_PACKAGE
                )
                .because("server orchestration depends only on common and public API contracts")
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    @Test
    void clientCodeDoesNotDependOnServerOrOptionalImplementations() {
        noClasses()
                .that().resideInAPackage(CLIENT_PACKAGE)
                .should().dependOnClassesThat().resideInAnyPackage(
                        SERVER_PACKAGE,
                        COMPAT_PACKAGE,
                        MIXIN_PACKAGE
                )
                .because("client presentation depends only on common and public API contracts")
                .check(productionClasses);
    }

    @Test
    void rootBootstrapDoesNotDependOnDirectionalImplementations() {
        noClasses()
                .that().resideInAPackage(ProjectIdentity.ROOT_PACKAGE)
                .should().dependOnClassesThat().resideInAnyPackage(
                        SERVER_PACKAGE,
                        CLIENT_PACKAGE,
                        COMPAT_PACKAGE,
                        MIXIN_PACKAGE
                )
                .because("the root bootstrap must stay physical-side-safe")
                .check(productionClasses);
    }

    @Test
    void publicApiDoesNotExposeInternalImplementationPackages() {
        noClasses()
                .that().resideInAPackage(API_PACKAGE)
                .should().dependOnClassesThat().resideInAnyPackage(
                        COMMON_PACKAGE,
                        SERVER_PACKAGE,
                        CLIENT_PACKAGE,
                        COMPAT_PACKAGE,
                        MIXIN_PACKAGE
                )
                .because("the public API must remain independently consumable")
                .allowEmptyShould(true)
                .check(productionClasses);
    }

    @Test
    void optionalModTypesStayInsideCompatImplementations() {
        noClasses()
                .that().resideOutsideOfPackage(COMPAT_PACKAGE)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.enviouse.progressivestages..",
                        "io.redspace.ironsspellbooks..",
                        "dev.latvian.mods.kubejs..",
                        "top.theillusivec4.curios..",
                        "dev.ftb.mods.ftbquests..",
                        "dev.ftb.mods.ftbteams..",
                        "mezz.jei..",
                        "dev.emi.emi..",
                        "snownee.jade..",
                        "mcp.mobius.waila..",
                        "vazkii.patchouli..",
                        "com.klikli_dev.modonomicon.."
                )
                .because("optional integrations must not break base-mod classloading when absent")
                .check(productionClasses);
    }
}
