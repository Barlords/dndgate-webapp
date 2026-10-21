package fr.barlords.dndgateapi.bootstrap.server;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.junit.CacheMode.FOREVER;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static fr.barlords.dndgateapi.bootstrap.PackagesAndLayers.BOOTSTRAP_PACKAGE;
import static fr.barlords.dndgateapi.bootstrap.PackagesAndLayers.SERVER_PACKAGE;

@AnalyzeClasses(
    packages = "fr.barlords.dndgateapi.server",
    cacheMode = FOREVER,
    importOptions = {DoNotIncludeTests.class})
public class ServerDevelopmentRulesTest {

  @ArchTest
  public static final ArchRule SERVER_DEVELOPMENT_RULE =
      classes()
          .that()
          .resideInAPackage(SERVER_PACKAGE)
          .should()
          .onlyHaveDependentClassesThat()
          .resideInAnyPackage(SERVER_PACKAGE, BOOTSTRAP_PACKAGE)
          .andShould()
          .onlyBeAccessed()
          .byAnyPackage(BOOTSTRAP_PACKAGE, SERVER_PACKAGE);

  @ArchTest
  public static final ArchRule repository_should_end_with_repository =
          classes()
                  .that().resideInAPackage(SERVER_PACKAGE)
                  .and().resideInAPackage("..repository..")
                  .should().haveSimpleNameEndingWith("Repository");

  @ArchTest
  public static final ArchRule adapter_should_end_with_adapter =
          classes()
                  .that().resideInAPackage(SERVER_PACKAGE)
                  .and().resideInAPackage("..adapter..")
                  .should().haveSimpleNameEndingWith("Adapter");
}
