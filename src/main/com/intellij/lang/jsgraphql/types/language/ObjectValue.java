/*
    The MIT License (MIT)

    Copyright (c) 2015 Andreas Marek and Contributors

    Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files
    (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
    publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do
    so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
    OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
    LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
    CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.intellij.lang.jsgraphql.types.language;


import com.google.common.collect.ImmutableList;
import com.intellij.lang.jsgraphql.types.Internal;
import com.intellij.lang.jsgraphql.types.PublicApi;
import com.intellij.lang.jsgraphql.types.collect.ImmutableKit;
import com.intellij.lang.jsgraphql.types.util.TraversalControl;
import com.intellij.lang.jsgraphql.types.util.TraverserContext;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.intellij.lang.jsgraphql.types.Assert.assertNotNull;
import static com.intellij.lang.jsgraphql.types.collect.ImmutableKit.emptyList;
import static com.intellij.lang.jsgraphql.types.language.NodeChildrenContainer.newNodeChildrenContainer;

@PublicApi
public class ObjectValue extends AbstractNode<ObjectValue> implements Value<ObjectValue> {

  private final ImmutableList<ObjectField> objectFields;

  public static final String CHILD_OBJECT_FIELDS = "objectFields";

  @Internal
  protected ObjectValue(List<ObjectField> objectFields,
                        SourceLocation sourceLocation,
                        List<Comment> comments,
                        IgnoredChars ignoredChars,
                        Map<String, String> additionalData,
                        @Nullable List<? extends Node> sourceNodes) {
    super(sourceLocation, comments, ignoredChars, additionalData, sourceNodes);
    this.objectFields = ImmutableList.copyOf(objectFields);
  }

  /**
   * alternative to using a Builder for convenience
   *
   * @param objectFields the list of field that make up this object value
   */
  public ObjectValue(List<ObjectField> objectFields) {
    this(objectFields, null, emptyList(), IgnoredChars.EMPTY, ImmutableKit.emptyMap(), null);
  }

  public List<ObjectField> getObjectFields() {
    return objectFields;
  }

  @Override
  public List<Node> getChildren() {
    return ImmutableList.copyOf(objectFields);
  }

  @Override
  public NodeChildrenContainer getNamedChildren() {
    return newNodeChildrenContainer()
      .children(CHILD_OBJECT_FIELDS, objectFields)
      .build();
  }

  @Override
  public ObjectValue withNewChildren(NodeChildrenContainer newChildren) {
    return transform(builder -> builder
      .objectFields(newChildren.getChildren(CHILD_OBJECT_FIELDS))
    );
  }

  @Override
  public boolean isEqualTo(Node o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    return true;
  }

  @Override
  public ObjectValue deepCopy() {
    return new ObjectValue(deepCopy(objectFields), getSourceLocation(), getComments(), getIgnoredChars(), getAdditionalData(),
                           getSourceNodes());
  }


  @Override
  public String toString() {
    return "ObjectValue{" +
           "objectFields=" + objectFields +
           '}';
  }

  @Override
  public TraversalControl accept(TraverserContext<Node> context, NodeVisitor visitor) {
    return visitor.visitObjectValue(this, context);
  }


  public static Builder newObjectValue() {
    return new Builder();
  }

  public ObjectValue transform(Consumer<Builder> builderConsumer) {
    Builder builder = new Builder(this);
    builderConsumer.accept(builder);
    return builder.build();
  }

  public static final class Builder implements NodeBuilder {
    private SourceLocation sourceLocation;
    private ImmutableList<ObjectField> objectFields = emptyList();
    private ImmutableList<Comment> comments = emptyList();
    private IgnoredChars ignoredChars = IgnoredChars.EMPTY;
    private Map<String, String> additionalData = new LinkedHashMap<>();
    private @Nullable List<? extends Node> sourceNodes;

    private Builder() {
    }

    private Builder(ObjectValue existing) {
      this.sourceLocation = existing.getSourceLocation();
      this.comments = ImmutableList.copyOf(existing.getComments());
      this.objectFields = ImmutableList.copyOf(existing.getObjectFields());
      this.additionalData = new LinkedHashMap<>(existing.getAdditionalData());
      this.sourceNodes = existing.getSourceNodes();
    }

    @Override
    public Builder sourceLocation(SourceLocation sourceLocation) {
      this.sourceLocation = sourceLocation;
      return this;
    }

    public Builder objectFields(List<ObjectField> objectFields) {
      this.objectFields = ImmutableList.copyOf(objectFields);
      return this;
    }

    public Builder objectField(ObjectField objectField) {
      this.objectFields = ImmutableKit.addToList(objectFields, objectField);
      return this;
    }

    @Override
    public Builder comments(List<Comment> comments) {
      this.comments = ImmutableList.copyOf(comments);
      return this;
    }

    @Override
    public Builder ignoredChars(IgnoredChars ignoredChars) {
      this.ignoredChars = ignoredChars;
      return this;
    }

    @Override
    public Builder additionalData(Map<String, String> additionalData) {
      this.additionalData = assertNotNull(additionalData);
      return this;
    }

    @Override
    public Builder additionalData(String key, String value) {
      this.additionalData.put(key, value);
      return this;
    }

    @Override
    public Builder sourceNodes(@Nullable List<? extends Node> sourceNodes) {
      this.sourceNodes = sourceNodes;
      return this;
    }

    public ObjectValue build() {
      return new ObjectValue(objectFields, sourceLocation, comments, ignoredChars, additionalData, sourceNodes);
    }
  }
}
