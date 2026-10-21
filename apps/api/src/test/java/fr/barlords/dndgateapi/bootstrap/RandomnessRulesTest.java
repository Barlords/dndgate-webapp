package fr.barlords.dndgateapi.bootstrap;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.junit.CacheMode.FOREVER;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
    packages = "fr.barlords.dndgateapi",
    cacheMode = FOREVER,
    importOptions = {DoNotIncludeTests.class})
public class RandomnessRulesTest {

  @ArchTest
  public static final ArchRule NO_PREDICTABLE_RANDOM =
      noClasses()
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("java.util.Random")
          .because(
              "codes and tokens must come from java.security.SecureRandom, "
                  + "the output of java.util.Random can be predicted");
}
