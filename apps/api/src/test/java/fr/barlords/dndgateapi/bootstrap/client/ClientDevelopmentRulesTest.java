package fr.barlords.dndgateapi.bootstrap.client;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.junit.CacheMode.FOREVER;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static fr.barlords.dndgateapi.bootstrap.PackagesAndLayers.BOOTSTRAP_PACKAGE;
import static fr.barlords.dndgateapi.bootstrap.PackagesAndLayers.CLIENT_PACKAGE;

@AnalyzeClasses(
    packages = "fr.barlords.dndgateapi.client",
    cacheMode = FOREVER,
    importOptions = {DoNotIncludeTests.class})
public class ClientDevelopmentRulesTest {

  @ArchTest
  public static final ArchRule CLIENT_DEVELOPMENT_RULE =
      classes()
          .that()
          .resideInAPackage(CLIENT_PACKAGE)
          .should()
          .onlyHaveDependentClassesThat()
          .resideInAnyPackage(CLIENT_PACKAGE, BOOTSTRAP_PACKAGE)
          .andShould()
          .onlyBeAccessed()
          .byClassesThat()
          .resideInAnyPackage(BOOTSTRAP_PACKAGE, CLIENT_PACKAGE)
          .andShould()
          .onlyHaveDependentClassesThat()
          .resideInAnyPackage(BOOTSTRAP_PACKAGE, CLIENT_PACKAGE);

  @ArchTest
  public static final ArchRule controller_should_be_annotate_with_RestController =
          classes()
                  .that().resideInAPackage(CLIENT_PACKAGE)
                  .and().resideInAPackage("..client.controller..")
                  .should().beAnnotatedWith(RestController.class);

  @ArchTest
  static final ArchRule controller_should_end_with_Controller =
          classes()
                  .that().resideInAPackage(CLIENT_PACKAGE)
                  .and().resideInAPackage("..controller..")
                  .should().haveSimpleNameEndingWith("Controller");
}
