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
package com.intellij.lang.jsgraphql.types.schema;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.lang.jsgraphql.types.DirectivesUtil;
import com.intellij.lang.jsgraphql.types.Internal;
import com.intellij.lang.jsgraphql.types.PublicApi;
import com.intellij.lang.jsgraphql.types.language.InputObjectTypeDefinition;
import com.intellij.lang.jsgraphql.types.language.InputObjectTypeExtensionDefinition;
import com.intellij.lang.jsgraphql.types.util.FpKit;
import com.intellij.lang.jsgraphql.types.util.TraversalControl;
import com.intellij.lang.jsgraphql.types.util.TraverserContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.intellij.lang.jsgraphql.types.Assert.*;
import static com.intellij.lang.jsgraphql.types.util.FpKit.getByName;
import static java.util.Collections.emptyList;

/**
 * graphql clearly delineates between the types of objects that represent the output of a query and input objects that
 * can be fed into a graphql mutation.  You can define objects as input to graphql via this class
 * <p>
 * See http://graphql.org/learn/schema/#input-types for more details on the concept
 */
@PublicApi
public class GraphQLInputObjectType
  implements GraphQLNamedInputType, GraphQLUnmodifiedType, GraphQLNullableType, GraphQLInputFieldsContainer, GraphQLDirectiveContainer {

  private final String name;
  private final String description;
  private final ImmutableMap<String, GraphQLInputObjectField> fieldMap;
  private final InputObjectTypeDefinition definition;
  private final ImmutableList<InputObjectTypeExtensionDefinition> extensionDefinitions;
  private final DirectivesUtil.DirectivesHolder directives;

  public static final String CHILD_FIELD_DEFINITIONS = "fieldDefinitions";
  public static final String CHILD_DIRECTIVES = "directives";

  /**
   * @param name        the name
   * @param description the description
   * @param fields      the fields
   * @deprecated use the {@link #newInputObject()} builder pattern instead, as this constructor will be made private in a future version.
   */
  @Internal
  @Deprecated(forRemoval = true)
  public GraphQLInputObjectType(String name, String description, List<GraphQLInputObjectField> fields) {
    this(name, description, fields, emptyList(), null);
  }

  /**
   * @param name        the name
   * @param description the description
   * @param fields      the fields
   * @param directives  the directives on this type element
   * @param definition  the AST definition
   * @deprecated use the {@link #newInputObject()} builder pattern instead, as this constructor will be made private in a future version.
   */
  @Internal
  @Deprecated(forRemoval = true)
  public GraphQLInputObjectType(String name,
                                String description,
                                List<GraphQLInputObjectField> fields,
                                List<GraphQLDirective> directives,
                                InputObjectTypeDefinition definition) {
    this(name, description, fields, directives, definition, emptyList());
  }

  public GraphQLInputObjectType(String name,
                                String description,
                                List<GraphQLInputObjectField> fields,
                                List<GraphQLDirective> directives,
                                InputObjectTypeDefinition definition,
                                List<InputObjectTypeExtensionDefinition> extensionDefinitions) {
    assertValidName(name);
    assertNotNull(fields, () -> "fields can't be null");
    assertNotNull(directives, () -> "directives cannot be null");

    this.name = name;
    this.description = description;
    this.definition = definition;
    this.extensionDefinitions = ImmutableList.copyOf(extensionDefinitions);
    this.directives = new DirectivesUtil.DirectivesHolder(directives);
    this.fieldMap = buildDefinitionMap(fields);
  }

  private ImmutableMap<String, GraphQLInputObjectField> buildDefinitionMap(List<GraphQLInputObjectField> fieldDefinitions) {
    return ImmutableMap.copyOf(FpKit.getByName(fieldDefinitions, GraphQLInputObjectField::getName,
                                               (fld1, fld2) -> assertShouldNeverHappen("Duplicated definition for field '%s' in type '%s'",
                                                                                       fld1.getName(), this.name)));
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getDescription() {
    return description;
  }

  public List<GraphQLInputObjectField> getFields() {
    return getFieldDefinitions();
  }

  public GraphQLInputObjectField getField(String name) {
    return fieldMap.get(name);
  }

  @Override
  public List<GraphQLDirective> getDirectives() {
    return directives.getDirectives();
  }

  @Override
  public Map<String, GraphQLDirective> getDirectivesByName() {
    return directives.getDirectivesByName();
  }

  @Override
  public Map<String, List<GraphQLDirective>> getAllDirectivesByName() {
    return directives.getAllDirectivesByName();
  }

  @Override
  public GraphQLDirective getDirective(String directiveName) {
    return directives.getDirective(directiveName);
  }

  @Override
  public GraphQLInputObjectField getFieldDefinition(String name) {
    return fieldMap.get(name);
  }

  @Override
  public List<GraphQLInputObjectField> getFieldDefinitions() {
    return ImmutableList.copyOf(fieldMap.values());
  }

  @Override
  public InputObjectTypeDefinition getDefinition() {
    return definition;
  }

  public List<InputObjectTypeExtensionDefinition> getExtensionDefinitions() {
    return extensionDefinitions;
  }

  /**
   * This helps you transform the current GraphQLInputObjectType into another one by starting a builder with all
   * the current values and allows you to transform it how you want.
   *
   * @param builderConsumer the consumer code that will be given a builder to transform
   * @return a new object based on calling build on that builder
   */
  public GraphQLInputObjectType transform(Consumer<Builder> builderConsumer) {
    Builder builder = newInputObject(this);
    builderConsumer.accept(builder);
    return builder.build();
  }

  @Override
  public TraversalControl accept(TraverserContext<GraphQLSchemaElement> context, GraphQLTypeVisitor visitor) {
    return visitor.visitGraphQLInputObjectType(this, context);
  }

  @Override
  public List<GraphQLSchemaElement> getChildren() {
    List<GraphQLSchemaElement> children = new ArrayList<>(fieldMap.values());
    children.addAll(directives.getDirectives());
    return children;
  }

  @Override
  public SchemaElementChildrenContainer getChildrenWithTypeReferences() {
    return SchemaElementChildrenContainer.newSchemaElementChildrenContainer()
      .children(CHILD_FIELD_DEFINITIONS, fieldMap.values())
      .children(CHILD_DIRECTIVES, directives.getDirectives())
      .build();
  }

  @Override
  public GraphQLInputObjectType withNewChildren(SchemaElementChildrenContainer newChildren) {
    return transform(builder ->
                       builder.replaceDirectives(newChildren.getChildren(CHILD_DIRECTIVES))
                         .replaceFields(newChildren.getChildren(CHILD_FIELD_DEFINITIONS))
    );
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final boolean equals(Object o) {
    return super.equals(o);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public final int hashCode() {
    return super.hashCode();
  }


  @Override
  public String toString() {
    return "GraphQLInputObjectType{" +
           "name='" + name + '\'' +
           ", description='" + description + '\'' +
           ", fieldMap=" + fieldMap +
           ", definition=" + definition +
           ", directives=" + directives +
           '}';
  }

  public static Builder newInputObject(GraphQLInputObjectType existing) {
    return new Builder(existing);
  }

  public static Builder newInputObject() {
    return new Builder();
  }

  @PublicApi
  public static class Builder extends GraphqlTypeBuilder {
    private InputObjectTypeDefinition definition;
    private List<InputObjectTypeExtensionDefinition> extensionDefinitions = emptyList();
    private final Map<String, GraphQLInputObjectField> fields = new LinkedHashMap<>();
    private final List<GraphQLDirective> directives = new ArrayList<>();

    public Builder() {
    }

    public Builder(GraphQLInputObjectType existing) {
      this.name = existing.getName();
      this.description = existing.getDescription();
      this.definition = existing.getDefinition();
      this.extensionDefinitions = existing.getExtensionDefinitions();
      this.fields.putAll(getByName(existing.getFields(), GraphQLInputObjectField::getName));
      DirectivesUtil.enforceAddAll(this.directives, existing.getDirectives());
    }

    @Override
    public Builder name(String name) {
      super.name(name);
      return this;
    }

    @Override
    public Builder description(String description) {
      super.description(description);
      return this;
    }

    @Override
    public Builder comparatorRegistry(GraphqlTypeComparatorRegistry comparatorRegistry) {
      super.comparatorRegistry(comparatorRegistry);
      return this;
    }

    public Builder definition(InputObjectTypeDefinition definition) {
      this.definition = definition;
      return this;
    }

    public Builder extensionDefinitions(List<InputObjectTypeExtensionDefinition> extensionDefinitions) {
      this.extensionDefinitions = extensionDefinitions;
      return this;
    }

    public Builder field(GraphQLInputObjectField field) {
      assertNotNull(field, () -> "field can't be null");
      fields.put(field.getName(), field);
      return this;
    }

    /**
     * Take a field builder in a function definition and apply. Can be used in a jdk8 lambda
     * e.g.:
     * <pre>
     *     {@code
     *      field(f -> f.name("fieldName"))
     *     }
     * </pre>
     *
     * @param builderFunction a supplier for the builder impl
     * @return this
     */
    public Builder field(UnaryOperator<GraphQLInputObjectField.Builder> builderFunction) {
      assertNotNull(builderFunction, () -> "builderFunction should not be null");
      GraphQLInputObjectField.Builder builder = GraphQLInputObjectField.newInputObjectField();
      builder = builderFunction.apply(builder);
      return field(builder);
    }

    /**
     * Same effect as the field(GraphQLFieldDefinition). Builder.build() is called
     * from within
     *
     * @param builder an un-built/incomplete GraphQLFieldDefinition
     * @return this
     */
    public Builder field(GraphQLInputObjectField.Builder builder) {
      return field(builder.build());
    }

    public Builder fields(List<GraphQLInputObjectField> fields) {
      fields.forEach(this::field);
      return this;
    }

    public Builder replaceFields(List<GraphQLInputObjectField> fields) {
      this.fields.clear();
      fields.forEach(this::field);
      return this;
    }

    public boolean hasField(String fieldName) {
      return fields.containsKey(fieldName);
    }

    /**
     * This is used to clear all the fields in the builder so far.
     *
     * @return the builder
     */
    public Builder clearFields() {
      fields.clear();
      return this;
    }

    public Builder withDirectives(GraphQLDirective... directives) {
      this.directives.clear();
      for (GraphQLDirective directive : directives) {
        withDirective(directive);
      }
      return this;
    }

    public Builder withDirective(GraphQLDirective directive) {
      assertNotNull(directive, () -> "directive can't be null");
      DirectivesUtil.enforceAdd(this.directives, directive);
      return this;
    }

    public Builder replaceDirectives(List<GraphQLDirective> directives) {
      assertNotNull(directives, () -> "directive can't be null");
      this.directives.clear();
      DirectivesUtil.enforceAddAll(this.directives, directives);
      return this;
    }

    public Builder withDirective(GraphQLDirective.Builder builder) {
      return withDirective(builder.build());
    }

    /**
     * This is used to clear all the directives in the builder so far.
     *
     * @return the builder
     */
    public Builder clearDirectives() {
      directives.clear();
      return this;
    }

    public GraphQLInputObjectType build() {
      return new GraphQLInputObjectType(
        name,
        description,
        sort(fields, GraphQLInputObjectType.class, GraphQLInputObjectField.class),
        sort(directives, GraphQLInputObjectType.class, GraphQLDirective.class),
        definition,
        extensionDefinitions);
    }
  }
}
