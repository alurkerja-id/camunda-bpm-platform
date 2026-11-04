/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.model.xml.impl.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.ModelException;
import org.camunda.bpm.model.xml.ModelInstance;
import org.camunda.bpm.model.xml.impl.ModelImpl;
import org.camunda.bpm.model.xml.impl.util.ModelTypeException;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.testmodel.TestModel;
import org.camunda.bpm.model.xml.testmodel.TestModelConstants;
import org.camunda.bpm.model.xml.testmodel.TestModelParser;
import org.camunda.bpm.model.xml.testmodel.instance.Animal;
import org.camunda.bpm.model.xml.testmodel.instance.Bird;
import org.camunda.bpm.model.xml.testmodel.instance.Egg;
import org.camunda.bpm.model.xml.testmodel.instance.FlightPartnerRef;
import org.camunda.bpm.model.xml.testmodel.instance.FlyingAnimal;
import org.camunda.bpm.model.xml.testmodel.instance.Wings;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.junit.Test;

/** Unit tests for {@link ModelElementTypeImpl} beyond what the shared animal test model exercises. */
public class ModelElementTypeImplTest {

  protected final Model testModel = TestModel.getTestModel();
  protected final ModelElementTypeImpl animalType = (ModelElementTypeImpl) testModel.getType(Animal.class);
  protected final ModelElementTypeImpl flyingAnimalType = (ModelElementTypeImpl) testModel.getType(FlyingAnimal.class);
  protected final ModelElementTypeImpl birdType = (ModelElementTypeImpl) testModel.getType(Bird.class);

  protected ModelElementTypeImpl standalone(String name) {
    ModelElementTypeImpl type = new ModelElementTypeImpl(new ModelImpl("standalone"), name, Animal.class);
    type.setTypeNamespace(TestModelConstants.MODEL_NAMESPACE);
    return type;
  }

  // registration ////////////////////////////////////////////////

  @Test
  public void shouldNotRegisterAttributeTwice() {
    ModelElementTypeImpl type = standalone("a");
    Attribute<?> attribute = animalType.getAttributes().get(0);

    type.registerAttribute(attribute);
    type.registerAttribute(attribute);

    assertThat(type.getAttributes()).containsExactly(attribute);
  }

  @Test
  public void shouldNotRegisterChildElementTypeTwice() {
    ModelElementTypeImpl type = standalone("a");

    type.registerChildElementType(birdType);
    type.registerChildElementType(birdType);

    assertThat(type.getChildElementTypes()).containsExactly(birdType);
  }

  @Test
  public void shouldNotRegisterChildElementCollectionTwice() {
    ModelElementTypeImpl type = standalone("a");
    ChildElementCollection<?> collection = flyingAnimalType.getChildElementCollections().get(0);

    type.registerChildElementCollection(collection);
    type.registerChildElementCollection(collection);

    assertThat(type.getChildElementCollections()).containsExactly(collection);
  }

  @Test
  public void shouldNotRegisterExtendingTypeTwice() {
    ModelElementTypeImpl type = standalone("a");

    type.registerExtendingType(birdType);
    type.registerExtendingType(birdType);

    assertThat(type.getExtendingTypes()).containsExactly(birdType);
    assertThatThrownBy(() -> type.getExtendingTypes().clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  // base types //////////////////////////////////////////////////

  @Test
  public void shouldAcceptSameBaseTypeAgainButRejectAnotherOne() {
    ModelElementTypeImpl type = standalone("a");
    ModelElementTypeImpl firstBase = standalone("first");
    ModelElementTypeImpl otherBase = standalone("other");

    type.setBaseType(firstBase);
    type.setBaseType(firstBase);

    assertThat(type.getBaseType()).isSameAs(firstBase);
    assertThatThrownBy(() -> type.setBaseType(otherBase))
      .isInstanceOf(ModelException.class)
      .hasMessageContaining("Type can not have multiple base types");
    assertThat(type.getBaseType()).isSameAs(firstBase);
  }

  @Test
  public void shouldResolveBaseTypesInOrderFromNearestToFurthest() {
    List<ModelElementType> baseTypes = new ArrayList<>();

    birdType.resolveBaseTypes(baseTypes);

    assertThat(baseTypes).containsExactly(flyingAnimalType, animalType);
    List<ModelElementType> none = new ArrayList<>();
    animalType.resolveBaseTypes(none);
    assertThat(none).isEmpty();
  }

  @Test
  public void shouldDetermineBaseTypeRelations() {
    assertThat(animalType.isBaseTypeOf(animalType)).isTrue();
    assertThat(animalType.isBaseTypeOf(birdType)).isTrue();
    assertThat(flyingAnimalType.isBaseTypeOf(birdType)).isTrue();
    assertThat(birdType.isBaseTypeOf(animalType)).isFalse();
    assertThat(birdType.isBaseTypeOf(testModel.getType(Egg.class))).isFalse();
  }

  // extending types /////////////////////////////////////////////

  @Test
  public void shouldCollectAllExtendingTypesTransitivelyIncludingItself() {
    Collection<ModelElementType> all = animalType.getAllExtendingTypes();

    assertThat(all).contains(animalType, flyingAnimalType, birdType);
    assertThat(birdType.getAllExtendingTypes()).containsExactly(birdType);
  }

  @Test
  public void shouldTerminateOnCyclicExtendingTypes() {
    ModelElementTypeImpl first = standalone("first");
    ModelElementTypeImpl second = standalone("second");
    first.registerExtendingType(second);
    second.registerExtendingType(first);

    assertThat(first.getAllExtendingTypes()).containsExactlyInAnyOrder(first, second);
  }

  @Test
  public void shouldVisitSharedExtendingTypeOnlyOnce() {
    ModelElementTypeImpl root = standalone("root");
    ModelElementTypeImpl left = standalone("left");
    ModelElementTypeImpl right = standalone("right");
    ModelElementTypeImpl shared = standalone("shared");
    root.registerExtendingType(left);
    root.registerExtendingType(right);
    left.registerExtendingType(shared);
    right.registerExtendingType(shared);

    assertThat(root.getAllExtendingTypes()).containsExactlyInAnyOrder(root, left, right, shared);
  }

  // inherited members ///////////////////////////////////////////

  @Test
  public void shouldIncludeInheritedChildElementCollections() {
    List<ChildElementCollection<?>> own = flyingAnimalType.getChildElementCollections();
    List<ChildElementCollection<?>> inherited = animalType.getAllChildElementCollections();

    List<ChildElementCollection<?>> all = flyingAnimalType.getAllChildElementCollections();

    assertThat(all).containsAll(own).containsAll(inherited);
    assertThat(all).hasSize(inherited.size() + own.size());
    assertThat(all.subList(0, inherited.size())).isEqualTo(inherited);
  }

  @Test
  public void shouldIncludeInheritedChildElementTypes() {
    List<ModelElementType> all = flyingAnimalType.getAllChildElementTypes();

    assertThat(all).containsAll(animalType.getAllChildElementTypes()).containsAll(flyingAnimalType.getChildElementTypes());
    assertThat(animalType.getAllChildElementTypes()).isEqualTo(animalType.getChildElementTypes());
  }

  @Test
  public void shouldIncludeInheritedAttributes() {
    Collection<Attribute<?>> all = birdType.getAllAttributes();

    assertThat(all).containsAll(birdType.getAttributes()).containsAll(flyingAnimalType.getAttributes()).containsAll(animalType.getAttributes());
  }

  @Test
  public void shouldFindAttributeByNameIncludingInheritedOnes() {
    Attribute<?> inherited = birdType.getAttribute(TestModelConstants.ATTRIBUTE_NAME_NAME);
    Attribute<?> own = birdType.getAttribute(TestModelConstants.ATTRIBUTE_NAME_WINGSPAN);

    assertThat(inherited).isNotNull();
    assertThat(inherited.getAttributeName()).isEqualTo(TestModelConstants.ATTRIBUTE_NAME_NAME);
    assertThat(own).isNotNull();
    assertThat(birdType.getAttribute("doesNotExist")).isNull();
    assertThat(animalType.getAttribute(TestModelConstants.ATTRIBUTE_NAME_WINGSPAN)).isNull();
  }

  @Test
  public void shouldFindChildElementCollectionByChildType() {
    ModelElementType partnerRefType = testModel.getType(FlightPartnerRef.class);

    ChildElementCollection<?> found = flyingAnimalType.getChildElementCollection(partnerRefType);

    assertThat(found).isNotNull();
    assertThat(found.getChildElementType(testModel)).isEqualTo(partnerRefType);
    assertThat(flyingAnimalType.getChildElementCollection(birdType)).isNull();
  }

  // instances ///////////////////////////////////////////////////

  @Test
  public void shouldNotInstantiateAbstractType() {
    ModelInstance modelInstance = new TestModelParser().parseModelFromStream(
        ModelElementTypeImplTest.class.getResourceAsStream("/org/camunda/bpm/model/xml/testmodel/instance/BirdTest.xml"));

    assertThat(flyingAnimalType.isAbstract()).isTrue();
    assertThatThrownBy(() -> flyingAnimalType.newInstance(modelInstance))
      .isInstanceOf(ModelTypeException.class)
      .hasMessageContaining("flyingAnimal is abstract and no instances can be created");
  }

  @Test
  public void shouldCreateInstanceOfConcreteType() {
    ModelInstance modelInstance = new TestModelParser().parseModelFromStream(
        ModelElementTypeImplTest.class.getResourceAsStream("/org/camunda/bpm/model/xml/testmodel/instance/BirdTest.xml"));

    ModelElementInstance created = birdType.newInstance(modelInstance);

    assertThat(created).isInstanceOf(Bird.class);
    assertThat(created.getElementType()).isEqualTo(birdType);
  }

  @Test
  public void shouldFindInstancesViaAlternativeNamespaces() {
    String mechanical = "http://camunda.org/mechanical";
    String yans = "http://camunda.org/yans";
    ModelImpl model = (ModelImpl) testModel;
    ModelInstance modelInstance;
    try (InputStream stream = ModelElementTypeImplTest.class.getResourceAsStream(
        "/org/camunda/bpm/model/xml/testmodel/instance/AlternativeNsTest.xml")) {
      modelInstance = new TestModelParser().parseModelFromStream(stream);
    } catch (java.io.IOException e) {
      throw new AssertionError(e);
    }
    ModelElementType wingsType = testModel.getType(Wings.class);

    // without alternatives nothing lives in the "electronic" namespace
    assertThat(wingsType.getInstances(modelInstance)).isEmpty();

    model.declareAlternativeNamespace(mechanical, TestModelConstants.NEWER_NAMESPACE);
    model.declareAlternativeNamespace(yans, TestModelConstants.NEWER_NAMESPACE);
    try {
      Collection<ModelElementInstance> wings = wingsType.getInstances(modelInstance);
      // the search stops at the first alternative namespace that has matches
      assertThat(wings).isNotEmpty();
      for (ModelElementInstance instance : wings) {
        assertThat(instance).isInstanceOf(Wings.class);
      }
    } finally {
      model.undeclareAlternativeNamespace(mechanical);
      model.undeclareAlternativeNamespace(yans);
    }
  }

  @Test
  public void shouldReturnNoInstancesForTypeNotUsedInDocument() {
    ModelInstance modelInstance = new TestModelParser().parseModelFromStream(
        ModelElementTypeImplTest.class.getResourceAsStream("/org/camunda/bpm/model/xml/testmodel/instance/BirdTest.xml"));

    assertThat(testModel.getType(Wings.class).getInstances(modelInstance)).isEmpty();
  }

  // equals / hashCode ///////////////////////////////////////////

  @Test
  public void shouldBeEqualOnModelNameAndNamespace() {
    ModelImpl model = new ModelImpl("m");
    ModelElementTypeImpl first = new ModelElementTypeImpl(model, "a", Animal.class);
    ModelElementTypeImpl second = new ModelElementTypeImpl(new ModelImpl("m"), "a", Bird.class);
    first.setTypeNamespace("ns");
    second.setTypeNamespace("ns");

    assertThat(first).isEqualTo(first).isEqualTo(second);
    assertThat(first.hashCode()).isEqualTo(second.hashCode());
    assertThat(first).isNotEqualTo(null).isNotEqualTo("a");
  }

  @Test
  public void shouldDifferWhenModelNameOrNamespaceDiffers() {
    ModelElementTypeImpl base = new ModelElementTypeImpl(new ModelImpl("m"), "a", Animal.class);
    base.setTypeNamespace("ns");
    ModelElementTypeImpl otherModel = new ModelElementTypeImpl(new ModelImpl("other"), "a", Animal.class);
    otherModel.setTypeNamespace("ns");
    ModelElementTypeImpl otherName = new ModelElementTypeImpl(new ModelImpl("m"), "b", Animal.class);
    otherName.setTypeNamespace("ns");
    ModelElementTypeImpl otherNamespace = new ModelElementTypeImpl(new ModelImpl("m"), "a", Animal.class);
    otherNamespace.setTypeNamespace("other");

    assertThat(base).isNotEqualTo(otherModel).isNotEqualTo(otherName).isNotEqualTo(otherNamespace);
  }

  @Test
  public void shouldHandleMissingModelNameAndNamespaceInEqualsAndHashCode() {
    ModelElementTypeImpl noModel = new ModelElementTypeImpl(null, "a", Animal.class);
    ModelElementTypeImpl noModelToo = new ModelElementTypeImpl(null, "a", Animal.class);
    ModelElementTypeImpl noName = new ModelElementTypeImpl(null, null, Animal.class);
    ModelElementTypeImpl noNameToo = new ModelElementTypeImpl(null, null, Animal.class);
    ModelElementTypeImpl withModel = new ModelElementTypeImpl(new ModelImpl("m"), "a", Animal.class);

    assertThat(noModel).isEqualTo(noModelToo);
    assertThat(noModel.hashCode()).isEqualTo(noModelToo.hashCode());
    assertThat(noName).isEqualTo(noNameToo);
    assertThat(noName.hashCode()).isEqualTo(noNameToo.hashCode());
    assertThat(noModel).isNotEqualTo(noName).isNotEqualTo(withModel);
    assertThat(withModel).isNotEqualTo(noModel);
    assertThat(noName).isNotEqualTo(noModel);
  }

  @Test
  public void shouldExposeConfiguredProperties() {
    ModelImpl model = new ModelImpl("m");
    ModelElementTypeImpl type = new ModelElementTypeImpl(model, "a", Bird.class);
    type.setTypeNamespace("ns");
    type.setAbstract(true);

    assertThat(type.getModel()).isSameAs(model);
    assertThat(type.getTypeName()).isEqualTo("a");
    assertThat(type.getTypeNamespace()).isEqualTo("ns");
    assertThat(type.getInstanceType()).isEqualTo(Bird.class);
    assertThat(type.isAbstract()).isTrue();
    assertThat(type.getBaseType()).isNull();
  }
}
