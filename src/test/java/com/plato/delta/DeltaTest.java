package com.plato.delta;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.plato.delta.AttributeMap.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Delta building")
class DeltaBuilderTest {

  @Nested
  public class Constructor {
    final OpList ops = new OpList();

    @BeforeEach
    public void beforeEach() {
      ops.add(Op.insert("abc"));
      ops.add(Op.retain(1, of("color", "red")));
      ops.add(Op.delete(4));
      ops.add(Op.insert("def", of("bold", true)));
      ops.add(Op.retain(6));
    }

    @Test
    public void empty() {
      var delta = new Delta();
      assertNotNull(delta);
      assertNotNull(delta.ops);
      assertEquals(delta.ops.size(), 0);
    }

    @Test
    public void emptyOps() {
      var delta = new Delta().insert("").delete(0).retain(0);
      assertNotNull(delta);
      assertNotNull(delta.ops);
      assertEquals(0, delta.ops.size());
    }

    @Test
    public void arrayOfOps() {
      var delta = new Delta(ops);
      assertEquals(ops, delta.ops);
    }

    @Test
    public void delta() {
      var original = new Delta(ops);
      var copy = new Delta(original);
      assertEquals(original.ops, copy.ops);
      assertEquals(ops, copy.ops);
    }
  }


  @Nested
  public class Insert {

    @Test
    public void insertText() {
      var delta = new Delta().insert("test");
      assertEquals(1, delta.ops.size());
      assertEquals(Op.insert("test"), delta.ops.get(0));
    }

    @Test
    public void insertTextNull() {
      var delta = new Delta().insert("test", null);
      assertEquals(1, delta.ops.size());
      assertEquals(Op.insert("test"), delta.ops.get(0));
    }

    @Test
    public void insertEmbedded() {
      var delta = new Delta().insert(1);
      assertEquals(1, delta.ops.size());
      assertEquals(Op.insert(1), delta.ops.get(0));
    }

    @Test
    public void insertEmbeddedWithAttribute() {
      var attribute = of("url", "https://plato.mantoux.org", "alt", "Plato");
      var delta = new Delta().insert(1, attribute);
      assertEquals(1, delta.ops.size());
      assertEquals(Op.insert(1, attribute), delta.ops.get(0));
    }

    @Test
    public void insertEmbeddedNonInteger() {
      var embed = Map.of("url", "https://plato.mantoux.org");
      var attribute = of("alt", "Plato");
      var delta = new Delta().insert(embed, attribute);
      assertEquals(1, delta.ops.size());
      assertEquals(Op.insert(embed, attribute), delta.ops.get(0));
    }

    @Test
    public void insertTextAttributes() {
      var delta = new Delta().insert("test", of("bold", true));
      assertEquals(1, delta.ops.size());
      assertEquals(Op.insert("test", of("bold", true)), delta.ops.get(0));
    }

    @Test
    public void insertTextAfterDelete() {
      var delta = new Delta().delete(1).insert("a");
      var expected = new Delta().insert("a").delete(1);
      assertEquals(expected, delta);
    }

    @Test
    public void insertTextAfterDeleteWithMerge() {
      var delta = new Delta().insert("a").delete(1).insert("b");
      var expected = new Delta().insert("ab").delete(1);
      assertEquals(expected, delta);
    }

    @Test
    public void insertTextAfterDeleteNoMerge() {
      var delta = new Delta().insert(1).delete(1).insert("a");
      var expected = new Delta().insert(1).insert("a").delete(1);
      assertEquals(expected, delta);
    }

    @Test
    public void insertTextEmptyAttribute() {
      var delta = new Delta().insert("a", new AttributeMap());
      assertEquals(new Delta().insert("a"), delta);
    }
  }


  @Nested
  public class Delete {

    @Test
    public void deletaZero() {
      var delta = new Delta().delete(0);
      assertEquals(0, delta.ops.size());
    }

    @Test
    public void deletePositive() {
      var delta = new Delta().delete(1);
      assertEquals(1, delta.ops.size());
      assertEquals(Op.delete(1), delta.ops.get(0));
    }
  }


  @Nested
  public class Retain {

    @Test
    public void retainZero() {
      var delta = new Delta().retain(0);
      assertEquals(0, delta.ops.size());
    }

    @Test
    public void retain() {
      var delta = new Delta().retain(2);
      assertEquals(1, delta.ops.size());
      assertEquals(Op.retain(2), delta.ops.get(0));
    }

    @Test
    public void retainEmptyAttribute() {
      var delta = new Delta().retain(2, new AttributeMap());
      assertEquals(1, delta.ops.size());
      assertEquals(Op.retain(2), delta.ops.get(0));
    }

    @Test
    public void retainWithAttribute() {
      var delta = new Delta().retain(1, of("bold", true));
      assertEquals(1, delta.ops.size());
      assertEquals(Op.retain(1, of("bold", true)), delta.ops.get(0));
    }

    @Test
    public void retainEmptyAttributeWithDelete() {
      var delta = new Delta().retain(2, new AttributeMap()).delete(1); //Delete prevents chop
      assertEquals(new Delta().retain(2).delete(1), delta);
    }
  }


  @Nested
  public class Push {

    @Test
    public void pushOnEmpty() {
      var delta = new Delta();
      delta.push(Op.insert("test"));
      assertEquals(1, delta.ops.size());
    }

    @Test
    public void pushConsecutiveDelete() {
      var delta = new Delta().delete(2);
      delta.push(Op.delete(3));
      assertEquals(1, delta.ops.size());
      assertEquals(Op.delete(5), delta.ops.get(0));
    }

    @Test
    public void pushConsecutiveText() {
      var delta = new Delta().insert("a");
      delta.push(Op.insert("b"));
      assertEquals(1, delta.ops.size());
      assertEquals(Op.insert("ab"), delta.ops.get(0));
    }

    @Test
    public void pushConsecutiveTextWithMatchingAttributes() {
      var delta = new Delta().insert("a", of("bold", true));
      delta.push(Op.insert("b", of("bold", true)));
      assertEquals(1, delta.ops.size());
      assertEquals(Op.insert("ab", of("bold", true)), delta.ops.get(0));
    }

    @Test
    public void pushConsecutiveRetainsWithMatchingAttributes() {
      var delta = new Delta().retain(1, of("bold", true));
      delta.push(Op.retain(3, of("bold", true)));
      assertEquals(1, delta.ops.size());
      assertEquals(Op.retain(4, of("bold", true)), delta.ops.get(0));
    }

    @Test
    public void pushConsecutiveTextWithMismatchedAttributes() {
      var delta = new Delta().insert("a", of("bold", true));
      delta.push(Op.insert("b"));
      assertEquals(2, delta.ops.size());
    }

    @Test
    public void pushConsecutiveRetainsWithMismatchedAttributes() {
      var delta = new Delta().retain(1, of("bold", true));
      delta.retain(3);
      assertEquals(2, delta.ops.size());
    }

    @Test
    public void pushConsecutiveEmbedsWithMatchingAttributes() {
      var delta = new Delta().insert(1, of("alt", "Description"));
      delta.push(Op.insert(Map.of("url", "https://plato.mantoux.org"), of("alt", "Plato")));
      assertEquals(2, delta.ops.size());
    }
  }

}









