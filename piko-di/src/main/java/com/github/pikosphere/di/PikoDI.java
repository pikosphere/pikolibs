package com.github.pikosphere.di;


import lombok.extern.slf4j.Slf4j;

import javax.inject.Qualifier;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
public class PikoDI {

    private static final String ERROR_ON_INJECTOR_BUILD = "Unable to build injector due to error: %s , error data: %s";

    private static final String PROVIDER_METHOD_NAME_PREFIX = "provide";

    private static final List<Class> qualifierMandatoryTypes = Arrays.asList(
            String.class,
            Integer.class,
            Float.class,
            Double.class,
            Long.class,
            Map.class,
            Set.class,
            List.class
    );

    /**
     * Method filter accepts the below methods
     * 1. static
     * 2. public
     * 3. name starting with PROVIDER_METHOD_NAME_PREFIX
     * 4. Non void return type
     */
    private static Predicate<Method> methodFilter = method -> {
        int modifiers = method.getModifiers();
        Class<?> returnType = method.getReturnType();

        return (Modifier.isStatic(modifiers)
                && Modifier.isPublic(modifiers)
                && method.getName().startsWith(PROVIDER_METHOD_NAME_PREFIX)
                && !(Void.TYPE.isAssignableFrom(returnType)));
    };

    private Set<Item> eligibleItems;
    private Map<ItemKey, Item> keyToItemMap;

    private PikoDI(Set<Item> items) {
        this.eligibleItems = items;
        this.keyToItemMap = getKeyToItemMap(items);
    }

    public static PikoDI create(Class... modules) {

        //Find the eligible classes from the set of provided ones
        //Ignores the Classes without any public static methods
        Optional<Set<Class>> eligibleClassesOption = getEligibleClasses(modules);

        if (eligibleClassesOption.isPresent() && !eligibleClassesOption.get().isEmpty()) {

            //We will be here if there are some eligible module classes

            //Sort the items into ones with duplicate and not duplicates
            Map<ItemCategory, Set<Item>> categorizedItemMap = sortUniqueItems(eligibleClassesOption.get());

            //throw Exception if there are any duplicate items with contextual data in the exception
            throwErrorOnInEligibleItems(categorizedItemMap, ErrorType.DUPLICATE_ITEMS);

            Map<ItemCategory, Set<Item>> eligibleIdentifiableItemMap = sortValidIdentifiableItems(categorizedItemMap.get(ItemCategory.ELIGIBLE_ITEMS));

            //throw Exception if there are any item with improper identifiers
            throwErrorOnInEligibleItems(eligibleIdentifiableItemMap, ErrorType.INVALID_IDENTIFIER);

            Set<ItemKey> itemKeysWithNoProviders = findItemKeysWithNoProviders(eligibleIdentifiableItemMap.get(ItemCategory.ELIGIBLE_ITEMS));

            throwErrorOnItemKeys(itemKeysWithNoProviders, ErrorType.NO_PROVIDERS);


            Map<ItemCategory, Set<Item>> cyclicItemMap = sortItemsWithCycles(eligibleIdentifiableItemMap.get(ItemCategory.ELIGIBLE_ITEMS));

            //throw Exception if there are any cyclic items with contextual data in the exception
            throwErrorOnInEligibleItems(cyclicItemMap, ErrorType.CYCLIC_DEPENDENCY_ITEMS);

            //return the PikoDI instance with the final set of Items
            return new PikoDI(cyclicItemMap.get(ItemCategory.ELIGIBLE_ITEMS));
        } else {
            return new PikoDI(Collections.EMPTY_SET);
        }
    }

    private static void throwErrorOnItemKeys(Set<ItemKey> itemKeysWithNoProviders, ErrorType errorType) {

        if (itemKeysWithNoProviders != null && !itemKeysWithNoProviders.isEmpty()) {
            String message = String.format(ERROR_ON_INJECTOR_BUILD, errorType.toString(), itemKeysWithNoProviders);
            String errorCode = errorType.name();
            Map<String, Object> errorData = new HashMap<>();
            errorData.put(errorCode, itemKeysWithNoProviders);
            throw new Exception(message, errorCode, errorData);
        }
    }

    private static Set<ItemKey> findItemKeysWithNoProviders(Set<Item> items) {
        Set<ItemKey> allDeps = getAllDependencies(items);
        return getItemkeysWithNoProviders(allDeps, items);
    }

    private static Set<ItemKey> getItemkeysWithNoProviders(Set<ItemKey> allDeps, Set<Item> items) {
        Set<ItemKey> itemKeysWithNoProviders = new HashSet<>();

        for (ItemKey itemKey : allDeps) {
            boolean hasProvider = false;
            for (Item item : items) {
                if (item.ownItemKey.equals(itemKey)) {
                    hasProvider = true;
                    break;
                }
            }

            if (!hasProvider) {
                itemKeysWithNoProviders.add(itemKey);
            }
        }

        return itemKeysWithNoProviders;
    }

    private static Set<ItemKey> getAllDependencies(Set<Item> items) {
        Set<ItemKey> allDeps = new HashSet<>();
        for (Item item : items) {
            if (item.dependentItemKeys != null && !item.dependentItemKeys.isEmpty()) {
                allDeps.addAll(item.dependentItemKeys);
            }
        }

        return allDeps;
    }

    private static Map<ItemCategory, Set<Item>> sortValidIdentifiableItems(Set<Item> items) {
        Set<Item> eligibleItems = new HashSet<>();
        Set<Item> inEligibleItems = new HashSet<>();

        for (Item item : items) {
            if (hasValidIdentifier(item)) {
                eligibleItems.add(item);
            } else {
                inEligibleItems.add(item);
            }
        }

        Map<ItemCategory, Set<Item>> itemCategorySetMap = new HashMap<>();

        if (!eligibleItems.isEmpty()) {
            itemCategorySetMap.put(ItemCategory.ELIGIBLE_ITEMS, eligibleItems);
        }

        if (!inEligibleItems.isEmpty()) {
            itemCategorySetMap.put(ItemCategory.IN_ELIGIBLE_ITEMS, inEligibleItems);
        }

        return itemCategorySetMap;
    }

    private static boolean hasValidIdentifier(Item newItem) {
        //eligibility rules for an item
        // 1. checks whether the given item has a valid ItemKey (identifier)
        Class itemClass = newItem.itemClass;
        Annotation qualifierAnnotation = newItem.qualifierAnnotation;

        //validating rule 1 mentioned above
        if (isQualifierMandatoryType(itemClass) && (qualifierAnnotation == null)) {

            log.warn("Item type {} from provider method {}.{} requires a mandatory qualifier annotation {} or derivatives",
                    itemClass,
                    newItem.factoryMethod.getDeclaringClass(),
                    newItem.factoryMethod.getName(),
                    Qualifier.class);
            return false;
        }

        return true;
    }

    private static Map<ItemCategory, Set<Item>> sortItemsWithCycles(Set<Item> items) {
        Map<ItemCategory, Set<Item>> finalItemMap = new HashMap<>();


        Map<ItemKey, Item> keyToItemMap = getKeyToItemMap(items);

        for (Item item : items) {
            List<ItemKey> visitedItemKeyList = new ArrayList<>();
            //add the current item to the list first
            visitedItemKeyList.add(item.ownItemKey);
            Set<ItemKey> resolvedDependenciesForItem = getResolvedDependenciesForItem(item, items, keyToItemMap, visitedItemKeyList);

            if (resolvedDependenciesForItem.contains(item.ownItemKey)) {
                //if this happens we have a cycle
                Set<Item> inEligibleItems;
                if (finalItemMap.containsKey(ItemCategory.IN_ELIGIBLE_ITEMS)) {
                    inEligibleItems = finalItemMap.get(ItemCategory.IN_ELIGIBLE_ITEMS);
                } else {
                    inEligibleItems = new LinkedHashSet<>();
                }
                inEligibleItems.add(item);
                finalItemMap.put(ItemCategory.IN_ELIGIBLE_ITEMS, inEligibleItems);
            } else {
                Set<Item> eligibleItems;
                if (finalItemMap.containsKey(ItemCategory.ELIGIBLE_ITEMS)) {
                    eligibleItems = finalItemMap.get(ItemCategory.ELIGIBLE_ITEMS);
                } else {
                    eligibleItems = new LinkedHashSet<>();
                }
                eligibleItems.add(item);
                finalItemMap.put(ItemCategory.ELIGIBLE_ITEMS, eligibleItems);
            }
        }


        return finalItemMap;
    }

    private static Map<ItemKey, Item> getKeyToItemMap(Set<Item> items) {
        Map<ItemKey, Item> keyItemMap = new HashMap<>();
        if (items != null && !items.isEmpty()) {
            for (Item item : items) {
                keyItemMap.put(item.ownItemKey, item);
            }
        }

        return keyItemMap;
    }

    private static Set<ItemKey> getResolvedDependenciesForItem(Item item, Set<Item> items, Map<ItemKey, Item> keyToItemMap, List<ItemKey> visitedItemKeys) {
        Set<ItemKey> resolvedDependenciesForItem = new LinkedHashSet<>();
        if (item.dependentItemKeys != null && !item.dependentItemKeys.isEmpty()) {
            for (ItemKey dependentItemKey : item.dependentItemKeys) {

                //check if there is a cycle
                if (visitedItemKeys.contains(dependentItemKey)) {
                    Map<String, Object> data = new HashMap<>();
                    String errorCode = ErrorType.CYCLIC_DEPENDENCY_ITEMS.name();
                    String message = String.format("Cyclic dependency identified for key %s", dependentItemKey);
                    List<ItemKey> newCyclicDepList = new ArrayList<>(visitedItemKeys);
                    //add the key with the cycle as well
                    newCyclicDepList.add(dependentItemKey);
                    data.put(errorCode, newCyclicDepList);
                    throw new PikoDI.Exception(message, errorCode, data);
                }

                Item dependentItem = keyToItemMap.get(dependentItemKey);
                visitedItemKeys.add(dependentItemKey);
                Set<ItemKey> resolvedDependenciesForDepItem = getResolvedDependenciesForItem(dependentItem, items, keyToItemMap, visitedItemKeys);
                //just add the dependentkey to this resolved list
                resolvedDependenciesForDepItem.add(dependentItemKey);

                //then add this to the main set and continue to the next dep key
                resolvedDependenciesForItem.addAll(resolvedDependenciesForDepItem);
            }

        }
        return resolvedDependenciesForItem;
    }

    private static void throwErrorOnInEligibleItems(Map<ItemCategory, Set<Item>> categorizedItemMap,
                                                    ErrorType errorType) {
        if (categorizedItemMap.containsKey(ItemCategory.IN_ELIGIBLE_ITEMS)) {
            Set<Item> itemSet = categorizedItemMap.get(ItemCategory.IN_ELIGIBLE_ITEMS);

            if (itemSet != null && !itemSet.isEmpty()) {
                String message = String.format(ERROR_ON_INJECTOR_BUILD, errorType.toString(), itemSet);
                String errorCode = errorType.name();
                Map<String, Object> errorData = new HashMap<>();
                errorData.put(errorCode, itemSet);
                throw new Exception(message, errorCode, errorData);
            }
        }
    }

    private static Map<ItemCategory, Set<Item>> sortUniqueItems(Set<Class> eligibleClasses) {
        Map<ItemCategory, Set<Item>> itemCategorySetMap = new HashMap<>();
        if (eligibleClasses != null && !eligibleClasses.isEmpty()) {
            for (Class clz : eligibleClasses) {
                //for each class get the items
                Map<ItemCategory, Set<Item>> categorizedMap = getItemMapForClass(clz);

                Set<Item> newIneligibleItems = null;

                if (categorizedMap.containsKey(ItemCategory.ELIGIBLE_ITEMS)) {
                    Set<Item> eligibleSet;
                    if (itemCategorySetMap.containsKey(ItemCategory.ELIGIBLE_ITEMS)) {
                        eligibleSet = itemCategorySetMap.get(ItemCategory.ELIGIBLE_ITEMS);
                    } else {
                        eligibleSet = new HashSet<>();
                    }

                    Set<Item> newEligibleItems = categorizedMap.get(ItemCategory.ELIGIBLE_ITEMS);

                    Map<ItemCategory, Set<Item>> uniqueItemsMap = getComparativeEligibleItemMap(newEligibleItems, eligibleSet);

                    if (uniqueItemsMap.containsKey(ItemCategory.IN_ELIGIBLE_ITEMS) && !uniqueItemsMap.get(ItemCategory.IN_ELIGIBLE_ITEMS).isEmpty()) {
                        newIneligibleItems = uniqueItemsMap.get(ItemCategory.IN_ELIGIBLE_ITEMS);
                    } else {
                        //add the new eligible items to the existing ones
                        eligibleSet.addAll(uniqueItemsMap.get(ItemCategory.ELIGIBLE_ITEMS));
                        itemCategorySetMap.put(ItemCategory.ELIGIBLE_ITEMS, eligibleSet);
                    }
                }

                if (categorizedMap.containsKey(ItemCategory.IN_ELIGIBLE_ITEMS)) {
                    Set<Item> inEligibleSet;
                    if (itemCategorySetMap.containsKey(ItemCategory.IN_ELIGIBLE_ITEMS)) {
                        inEligibleSet = itemCategorySetMap.get(ItemCategory.IN_ELIGIBLE_ITEMS);
                    } else {
                        inEligibleSet = new HashSet<>();
                    }
                    inEligibleSet.addAll(categorizedMap.get(ItemCategory.IN_ELIGIBLE_ITEMS));
                    if (newIneligibleItems != null && !newIneligibleItems.isEmpty()) {
                        //add the ineligible items from the eligible set here
                        inEligibleSet.addAll(categorizedMap.get(ItemCategory.IN_ELIGIBLE_ITEMS));
                    }
                    itemCategorySetMap.put(ItemCategory.IN_ELIGIBLE_ITEMS, inEligibleSet);
                } else {
                    //Though the original map for a given class did not have in eligible items, we should on inbetween
                    //just add the new ineligible set to a new set
                    if (newIneligibleItems != null && !newIneligibleItems.isEmpty()) {
                        //add the ineligible items from the eligible set here
                        itemCategorySetMap.put(ItemCategory.IN_ELIGIBLE_ITEMS, newIneligibleItems);
                    }
                }
            }
        }
        return itemCategorySetMap;
    }

    private static Map<ItemCategory, Set<Item>> getComparativeEligibleItemMap(Set<Item> newEligibleItems,
                                                                              Set<Item> eligibleSet) {

        Map<ItemCategory, Set<Item>> uniqueItemMap = new HashMap<>();

        for (Item newEligibleItem : newEligibleItems) {
            if (eligibleSet.contains(newEligibleItem)) {
                //means this is a duplicate. There are more than one provider for a ItemKey
                Set<Item> inEligibleSet;

                if (uniqueItemMap.containsKey(ItemCategory.IN_ELIGIBLE_ITEMS)) {
                    inEligibleSet = uniqueItemMap.get(ItemCategory.IN_ELIGIBLE_ITEMS);
                } else {
                    inEligibleSet = new HashSet<>();
                }

                //add the perceived new eligible item to the ineligible list
                inEligibleSet.add(newEligibleItem);
                uniqueItemMap.put(ItemCategory.IN_ELIGIBLE_ITEMS, inEligibleSet);
            } else {
                //means this is not a duplicate. There is not provider for the same ItemKey till now
                Set<Item> tempEligibleSet;

                if (uniqueItemMap.containsKey(ItemCategory.ELIGIBLE_ITEMS)) {
                    tempEligibleSet = uniqueItemMap.get(ItemCategory.ELIGIBLE_ITEMS);
                } else {
                    tempEligibleSet = new HashSet<>();
                }

                //add the perceived new eligible item to the ineligible list
                tempEligibleSet.add(newEligibleItem);
                uniqueItemMap.put(ItemCategory.ELIGIBLE_ITEMS, tempEligibleSet);

            }
        }


        return uniqueItemMap;
    }

    private static Map<ItemCategory, Set<Item>> getItemMapForClass(Class clz) {

        //filter the methods in the class as per the "methodFilter"
        Set<Method> eligibleMethods = getEligibleMethods(clz);

        return getItemMap(eligibleMethods);
    }

    private static Map<ItemCategory, Set<Item>> getItemMap(Set<Method> eligibleMethods) {
        // here we will get only methods with meet the method filter criteria
        Set<Item> eligibleItems = new HashSet<>();
        Set<Item> inEligibleItems = new HashSet<>();

        for (Method method : eligibleMethods) {
            Item newItem = getItemForMethod(method);
            if (isEligibleItem(newItem)) {
                eligibleItems.add(newItem);
            } else {
                inEligibleItems.add(newItem);
            }
        }

        Map<ItemCategory, Set<Item>> itemCategorySetMap = new HashMap<>();

        if (!eligibleItems.isEmpty()) {
            itemCategorySetMap.put(ItemCategory.ELIGIBLE_ITEMS, eligibleItems);
        }

        if (!inEligibleItems.isEmpty()) {
            itemCategorySetMap.put(ItemCategory.IN_ELIGIBLE_ITEMS, inEligibleItems);
        }

        return itemCategorySetMap;
    }

    private static boolean isEligibleItem(Item newItem) {

        //eligibility rules for an item
        // 1. The Items type and qualifier cannot be the same as one of its dependents. This is to avoid self dependency
        Class itemClass = newItem.itemClass;

        int requiredIndex = providedItemAlsoRequiredIndex(newItem);

        if (requiredIndex >= 0) {
            log.warn("Item type {} from provider method {}.{} is also in the declared required dependencies @ index {}." +
                            "Cannot provide and require the same Item from the same provider method!!",
                    itemClass,
                    newItem.factoryMethod.getDeclaringClass(),
                    newItem.factoryMethod.getName(),
                    requiredIndex);
            return false;
        }


        //since the checks passed we can return true
        return true;
    }

    private static int providedItemAlsoRequiredIndex(Item newItem) {

        Class clz = newItem.itemClass;
        Annotation qualifierAnnotation = newItem.qualifierAnnotation;

        Set<ItemKey> dependentItemKeys = newItem.dependentItemKeys;

        int returnIndex = -1;

        if (dependentItemKeys != null && dependentItemKeys.size() > 0) {

            ItemKey[] itemKeys = dependentItemKeys.toArray(new ItemKey[0]);

            //ItemKey keyToMatch = new ItemKey(clz,qualifierAnnotation);

            for (int i = 0; i < itemKeys.length; i++) {
                ItemKey itemKey = itemKeys[i];

                if (newItem.isOwnKeyEqualTo(itemKey)) {
                    returnIndex = i;
                    break;
                }
            }
        }

        //by default if the there is no match just return -1 which means there was no match
        return returnIndex;
    }

    private static boolean isQualifierMandatoryType(Class itemClass) {
        return qualifierMandatoryTypes.contains(itemClass);
    }

    private static Item getItemForMethod(Method method) {
        Class<?> returnType = method.getReturnType();

        Annotation qualifierAnnotation = getQualifierAnnotation(method, returnType, true, null, -1);

        Parameter[] parameters = method.getParameters();

        Set<ItemKey> dependentItemKeys = getItemKeysForParameters(method, parameters);

        Item itemForMethod;

        if (qualifierAnnotation == null) {
            itemForMethod = new Item(returnType, dependentItemKeys, method);
        } else {
            itemForMethod = new Item(returnType, qualifierAnnotation, dependentItemKeys, method);
        }


        return itemForMethod;
    }

    private static Set<ItemKey> getItemKeysForParameters(Method method, Parameter[] parameters) {

        //Note this is a linked hash set to maintain the insertion order which is very important during the
        // instance lookup phase in the injector
        Set<ItemKey> orderedItemKeySet = new LinkedHashSet<>();

        if (parameters != null && parameters.length > 0) {
            for (int i = 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];

                Class<?> paramClass = parameter.getType();

                Annotation qualifierAnnotation = getQualifierAnnotation(method, paramClass, false, parameter, i);

                ItemKey itemKey = new ItemKey(paramClass, qualifierAnnotation);

                orderedItemKeySet.add(itemKey);
            }

        }

        return orderedItemKeySet;
    }

    private static Annotation getQualifierAnnotation(Method method, Class<?> clz, boolean isReturnType, Parameter parameter, int paramIndex) {

        Annotation[] annotations;
        Annotation qualifierAnnotation = null;

        if (isReturnType) {
            annotations = method.getAnnotations();
        } else {
            //we assume these are parameters then
            annotations = parameter.getAnnotations();
        }

        Set<Annotation> qualifierAnnotations = Arrays.stream(annotations).filter(annotation -> annotation.annotationType().isAnnotationPresent(Qualifier.class)).collect(Collectors.toSet());

        if (!qualifierAnnotations.isEmpty()) {
            qualifierAnnotation = qualifierAnnotations.toArray(new Annotation[0])[0];
            if (qualifierAnnotations.size() > 1) {
                if (isReturnType) {
                    log.info("{}.{} has more than one qualifier annotations {}, so only the first one {} will be considered for lookup!", method.getDeclaringClass(),
                            method.getName(), qualifierAnnotations, qualifierAnnotation);
                } else {
                    log.info("{}.{}'s parameter no: {} of type {} has more than one qualifier annotations {}, so only the first one {} will be considered for lookup!",
                            method.getDeclaringClass(), method.getName(), paramIndex, clz, qualifierAnnotations, qualifierAnnotation);
                }
            }
        }

        return qualifierAnnotation;
    }

    private static Set<Method> getEligibleMethods(Class clz) {
        return Arrays.stream(clz.getDeclaredMethods()).filter(methodFilter).collect(Collectors.toSet());
    }

    private static Optional<Set<Class>> getEligibleClasses(Class[] modules) {
        Set<Class> eligibleClassesOption = null;
        if (modules != null) {
            List<Class> classList = Arrays.asList(modules);

            eligibleClassesOption = classList.stream().filter(clz -> !Arrays.stream(clz.getDeclaredMethods()).filter(methodFilter).collect(Collectors.toSet()).isEmpty()).collect(Collectors.toSet());

        }
        return Optional.ofNullable(eligibleClassesOption);
    }

    public <T> T getInstanceOf(ItemKey<T> itemKey) {
        if (canProvide(itemKey)) {
            T instanceObject;
            Item item = keyToItemMap.get(itemKey);

            Object[] params = new Object[0];

            if (item.dependentItemKeys != null && !item.dependentItemKeys.isEmpty()) {
                //there are dependencies, so instatiate them as well

                params = new Object[item.dependentItemKeys.size()];
                ItemKey[] dependentKeys = item.dependentItemKeys.toArray(new ItemKey[0]);
                for (int i = 0; i < dependentKeys.length; i++) {
                    try {
                        Object paramObj = getInstanceOf(dependentKeys[i]);
                        params[i] = paramObj;
                    } catch (Exception e) {
                        Map<String, Object> data = new HashMap<>();
                        String message = String.format("Failed to instantiate for Key %s, due to error %s",
                                dependentKeys[i], e.getMessage());
                        String errorCode = ErrorType.INSTANTIATION_FAILURE.name();
                        data.put(errorCode, e);
                        throw new PikoDI.Exception(message, errorCode, data);
                    }
                }
            }
            try {
                instanceObject = (T) item.factoryMethod.invoke(null, params);
                return instanceObject;
            } catch (java.lang.Exception e) {
                Map<String, Object> data = new HashMap<>();
                String message = String.format("Failed to instantiate for Key %s, due to error %s",
                        itemKey, e.getMessage());
                String errorCode = ErrorType.INSTANTIATION_FAILURE.name();
                data.put(errorCode, e);
                throw new PikoDI.Exception(message, errorCode, data);
            }

        } else {
            String message = String.format("ItemKey %s is registered in the system", itemKey);
            String errorCode = ErrorType.NO_PROVIDERS.name();
            Map<String, Object> data = new HashMap<>();
            data.put(errorCode, itemKey);
            throw new PikoDI.Exception(message, errorCode, data);
        }
    }

    public <T> boolean canProvide(ItemKey<T> itemKey) {
        return keyToItemMap.containsKey(itemKey);
    }

    @Override
    public String toString() {
        return "PikoDI{" +
                "eligibleItems=" + eligibleItems +
                '}';
    }


    private enum ItemCategory {
        ELIGIBLE_ITEMS,
        IN_ELIGIBLE_ITEMS
    }

    private enum ErrorType {
        DUPLICATE_ITEMS,
        CYCLIC_DEPENDENCY_ITEMS,
        INVALID_IDENTIFIER, NO_PROVIDERS, UNKNOWN,
        INSTANTIATION_FAILURE
    }

    public static class Exception extends RuntimeException {
        private String errorCode;
        private Map<String, Object> data;

        public Exception(String errorCode, Map<String, Object> data) {
            this.errorCode = errorCode;
            this.data = data;
        }

        public Exception(String message, String errorCode, Map<String, Object> data) {
            super(message);
            this.errorCode = errorCode;
            this.data = data;
        }

        public Exception(String message, Throwable cause, String errorCode, Map<String, Object> data) {
            super(message, cause);
            this.errorCode = errorCode;
            this.data = data;
        }

        public Exception(Throwable cause, String errorCode, Map<String, Object> data) {
            super(cause);
            this.errorCode = errorCode;
            this.data = data;
        }

        public Exception(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, String errorCode, Map<String, Object> data) {
            super(message, cause, enableSuppression, writableStackTrace);
            this.errorCode = errorCode;
            this.data = data;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public Map<String, Object> getData() {
            return data;
        }
    }

    private static class Item {

        private ItemKey<?> ownItemKey;
        private Class<?> itemClass;
        private Annotation qualifierAnnotation;
        private Set<ItemKey> dependentItemKeys;
        private Method factoryMethod;

        Item(Class<?> itemClass, Annotation qualifierAnnotation, Set<ItemKey> dependentItemKeys, Method method) {
            this.itemClass = itemClass;
            this.qualifierAnnotation = qualifierAnnotation;
            this.dependentItemKeys = dependentItemKeys;
            this.ownItemKey = new ItemKey<Object>(itemClass, qualifierAnnotation);
            this.factoryMethod = method;
        }

        Item(Class<?> itemClass, Set<ItemKey> dependentItemKeys, Method method) {
            this.itemClass = itemClass;
            this.dependentItemKeys = dependentItemKeys;
            this.ownItemKey = new ItemKey<Object>(itemClass);
            this.factoryMethod = method;
        }

        boolean isOwnKeyEqualTo(ItemKey otherItemKey) {
            return this.ownItemKey.equals(otherItemKey);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Item)) return false;
            Item item = (Item) o;
            return ownItemKey.equals(item.ownItemKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ownItemKey);
        }

        @Override
        public String toString() {
            return "Item{" +
                    "ownItemKey=" + ownItemKey +
                    ", itemClass=" + itemClass +
                    ", qualifierAnnotation=" + qualifierAnnotation +
                    ", dependentItemKeys=" + dependentItemKeys +
                    ", factoryMethod=" + factoryMethod +
                    '}';
        }
    }
}
