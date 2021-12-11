package io.confluent.ksql.test.util;

import java.util.Optional;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

public final class OptionalMatchers {

  private OptionalMatchers() {
  }

  public static <T> Matcher<Optional<T>> of(final Matcher<T> valueMatcher) {
    return new TypeSafeDiagnosingMatcher<Optional<T>>() {
      @Override
      protected boolean matchesSafely(
          final Optional<T> item,
          final Description mismatchDescription
      ) {
        if (!item.isPresent()) {
          mismatchDescription.appendText("not present");
          return false;
        }

        if (!valueMatcher.matches(item.get())) {
          valueMatcher.describeMismatch(item.get(), mismatchDescription);
          return false;
        }

        return true;
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText("optional ").appendDescriptionOf(valueMatcher);
      }
    };
  }
}