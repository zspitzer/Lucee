# LDEV-2919: Component-level localmode Implementation Plan

## Summary

Add `localmode` attribute support to `cfcomponent` tag, allowing localmode inheritance: Server → Application → **Component** → Function

## Review Notes (2025-12-11)

Issues found and corrected in this plan:

1. ~~Wrong constant values~~ - Fixed: classic=1, modern=2 (not 0 and 1)
2. ~~Bytecode section said "Integer"~~ - Fixed: using primitive `int`
3. Added verification: `ApplicationContext.getLocalMode()` returns primitive `int`
4. Added verification: Static functions have `ownerComponent` - no special handling needed
5. Added detail: Parent component inheritance resolved lazily in `getLocalMode()` because `base` isn't set until after `initComponent()` runs
6. Added precise code locations and before/after examples

## Current Inheritance Chain (UDFImpl._call() line 317)
```java
int oldCheckArgs = undefined.setMode(
    properties.getLocalMode() == null
        ? pc.getApplicationContext().getLocalMode()
        : properties.getLocalMode().intValue()
);
```

## Performance Optimisation

Use primitive `int` instead of `Integer` for ComponentProperties to avoid boxing overhead.

### Constant Values (from Undefined.java)

```java
MODE_NO_LOCAL_AND_ARGUMENTS = 0               // no local/arguments scope lookup
MODE_LOCAL_OR_ARGUMENTS_ONLY_WHEN_EXISTS = 1  // classic
MODE_LOCAL_OR_ARGUMENTS_ALWAYS = 2            // modern
```

### Component localMode values

- `-1` = inherit (from parent component or Application.cfc)
- `1` = classic (`Undefined.MODE_LOCAL_OR_ARGUMENTS_ONLY_WHEN_EXISTS`)
- `2` = modern (`Undefined.MODE_LOCAL_OR_ARGUMENTS_ALWAYS`)

**Key insight:** Component localmode is static per component, but parent inheritance is resolved **lazily** on first access (because `base` reference isn't set until after `initComponent()` runs). Application.cfc inheritance CANNOT be resolved at load time because Application.cfc settings can change between requests.

When a component extends another:

- If child has explicit localmode → use it
- Otherwise → walk up parent chain via `getLocalMode()` until we find an explicit value or return -1

This way, ComponentImpl stores its own declared value, `getLocalMode()` resolves parent inheritance, and UDFImpl just does:

1. Check UDF's own localmode
2. Check component's localmode (already resolved from parent components)
3. Fall back to Application.cfc (request-time lookup)

### ApplicationContext.getLocalMode() return type
Verified: Returns primitive `int` (not Integer), so no unboxing overhead at runtime.

### Static functions

Verified: Static functions DO have `ownerComponent` set via `setOwnerComponent()` call in ComponentImpl line 2651. No special handling needed.

## Files to Modify

### 1. ComponentProperties.java
**Path:** `core/src/main/java/lucee/runtime/ComponentProperties.java`

Changes:

- Add field `final int localMode;` after `final int modifier;` (line 47) - grouping int fields for memory alignment:
```java
// int fields (4 bytes each)
final int modifier;
final int localMode;  // ADD THIS: -1=inherit, 1=classic, 2=modern
```

- Update constructor signature (line 56) to accept `int localMode` after `int modifier`:
```java
public ComponentProperties(String name, String dspName, String extend, String implement,
    String hint, Boolean output, String callPath, boolean realPath, String subName,
    boolean _synchronized, Class javaAccessClass, boolean persistent, boolean accessors,
    int modifier, int localMode, Struct meta)  // ADD localMode param
```

- Add assignment in constructor body: `this.localMode = localMode;`

- Update `duplicate()` method (line 75) to pass localMode:
```java
ComponentProperties cp = new ComponentProperties(name, dspName, extend, implement, hint,
    output, callPath, realPath, subName, _synchronized, javaAccessClass, persistent,
    accessors, modifier, localMode, meta);  // ADD localMode
```

### 2. Component.java (interface - loader module)
**Path:** `loader/src/main/java/lucee/runtime/Component.java`

Changes:

- Add method: `public int getLocalMode();`

### 3. ComponentImpl.java
**Path:** `core/src/main/java/lucee/runtime/ComponentImpl.java`

Changes:

- Keep existing 15-param constructor for backwards compatibility with old bytecode:
```java
// Backwards compatibility constructor - old bytecode calls this
public ComponentImpl(ComponentPageImpl componentPage, Boolean output, boolean _synchronized,
    String extend, String implement, String hint, String dspName, String callPath,
    boolean realPath, String style, boolean persistent, boolean accessors,
    int modifier, boolean isExtended, StructImpl meta) throws ApplicationException {
    this(componentPage, output, _synchronized, extend, implement, hint, dspName,
         callPath, realPath, style, persistent, accessors, modifier, -1, isExtended, meta);
}
```

- Add new 16-param constructor with localMode:
```java
// New constructor with localMode support
public ComponentImpl(ComponentPageImpl componentPage, Boolean output, boolean _synchronized,
    String extend, String implement, String hint, String dspName, String callPath,
    boolean realPath, String style, boolean persistent, boolean accessors,
    int modifier, int localMode, boolean isExtended, StructImpl meta) throws ApplicationException {
    // existing implementation, passing localMode to ComponentProperties
}
```

- Pass localMode to ComponentProperties. Note: inheritance from parent component is resolved AFTER `initComponent()` calls `_initComponent()` which sets up the `base` reference:
```java
this.properties = new ComponentProperties(componentPage.getComponentName(), dspName, extend.trim(),
    implement, hint, output, callPath + appendix, realPath, componentPage.getSubname(),
    _synchronized, null, persistent, accessors, modifier, localMode, meta);
```

- Add method to resolve localMode with parent inheritance:
```java
@Override
public int getLocalMode() {
    int lm = properties.localMode;
    if (lm < 0 && base != null) {
        lm = base.getLocalMode();
    }
    return lm;  // -1 means inherit from Application.cfc
}
```

**Note:** We resolve parent component inheritance lazily in `getLocalMode()` rather than in the constructor because `base` isn't set until after `initComponent()` runs.

### 4. UDFImpl.java
**Path:** `core/src/main/java/lucee/runtime/type/UDFImpl.java`

Changes at line 317 - update the localMode resolution:
```java
int lm = properties.getLocalMode() != null ? properties.getLocalMode().intValue() : -1;
if (lm < 0 && ownerComponent != null) lm = ownerComponent.getLocalMode();
if (lm < 0) lm = pc.getApplicationContext().getLocalMode();
int oldCheckArgs = undefined.setMode(lm);
```

### 5. PageImpl.java (bytecode generation)
**Path:** `core/src/main/java/lucee/transformer/bytecode/PageImpl.java`

Changes:

- Line ~251: Update constructor signature constant:
```java
// Before:
private static final Method CONSTR_COMPONENT_IMPL15 = new Method("<init>", Types.VOID,
    new Type[] { Types.COMPONENT_PAGE_IMPL, Types.BOOLEAN, Types.BOOLEAN_VALUE, Types.STRING, Types.STRING,
        Types.STRING, Types.STRING, Types.STRING, Types.BOOLEAN_VALUE, Types.STRING, Types.BOOLEAN_VALUE,
        Types.BOOLEAN_VALUE, Types.INT_VALUE, Types.BOOLEAN_VALUE, Types.STRUCT_IMPL });

// After (add Types.INT_VALUE before BOOLEAN_VALUE for isExtended):
private static final Method CONSTR_COMPONENT_IMPL16 = new Method("<init>", Types.VOID,
    new Type[] { Types.COMPONENT_PAGE_IMPL, Types.BOOLEAN, Types.BOOLEAN_VALUE, Types.STRING, Types.STRING,
        Types.STRING, Types.STRING, Types.STRING, Types.BOOLEAN_VALUE, Types.STRING, Types.BOOLEAN_VALUE,
        Types.BOOLEAN_VALUE, Types.INT_VALUE, Types.INT_VALUE, Types.BOOLEAN_VALUE, Types.STRUCT_IMPL });
//                                                               ^modifier     ^localMode  ^isExtended
```

- In `writeOutNewComponent()` at line ~1867, add localmode handling after modifier push (line 1878) and before `ILOAD 4` (line 1879):
```java
// Current code at line 1876-1879:
adapter.push(persistent);
adapter.push(accessors);
adapter.push(modifiers);
adapter.visitVarInsn(Opcodes.ILOAD, 4);  // isExtended

// New code - insert localmode after modifiers:
adapter.push(persistent);
adapter.push(accessors);
adapter.push(modifiers);

// localmode (int: -1=inherit, 1=classic, 2=modern)
attr = component.removeAttribute("localmode");
if (attr != null) {
    LitString ls = (LitString) component.getFactory().toExprString(attr.getValue());
    int mode = AppListenerUtil.toLocalMode(ls.getString(), -1);
    adapter.push(mode);
} else {
    adapter.push(-1);  // inherit from parent/application
}

adapter.visitVarInsn(Opcodes.ILOAD, 4);  // isExtended
```

- Update line 1884: `CONSTR_COMPONENT_IMPL15` → `CONSTR_COMPONENT_IMPL16`

### 6. core-base.tld (tag library definition)
**Path:** `core/src/main/java/resource/tld/core-base.tld`

Add after line ~498 (after `modifier` attribute):
```xml
<attribute>
    <type>string</type>
    <name>localMode</name>
    <required>false</required>
    <rtexprvalue>true</rtexprvalue>
    <values>modern,classic,true,false</values>
    <description>Specifies the local variable mode for all functions within this component.
        'modern' or 'true' requires explicit var/local scope for variables.
        'classic' or 'false' allows variables scope leakage.
        If not specified, inherits from Application.cfc or server default.</description>
</attribute>
```

### 7. Component evaluator (validation)
**Path:** `core/src/main/java/lucee/transformer/cfml/evaluator/impl/Component.java`

Add validation for localmode attribute (similar to modifier validation at line ~250):
```java
// localmode
attr = tag.getAttribute("localmode");
if (attr != null) {
    Expression expr = tag.getFactory().toExprString(attr.getValue());
    if (!(expr instanceof LitString))
        throw new EvaluatorException("Attribute [localmode] of the tag [" + tlt.getFullName() + "], must contain a literal string value");
    LitString ls = (LitString) expr;
    int lm = AppListenerUtil.toLocalMode(ls.getString(), -1);
    if (lm == -1)
        throw new EvaluatorException("Value [" + ls.getString() + "] from attribute [localmode] of the tag [" + tlt.getFullName() + "] is invalid, valid values are [modern, classic, true, false]");
}
```

### 8. ComponentImpl.java - getMetaData() update

**Path:** `core/src/main/java/lucee/runtime/ComponentImpl.java`

Add localmode to component metadata (find `getMetaData()` method, add after other properties):

```java
// localmode - include resolved value for visibility
int lm = getLocalMode();
if (lm > 0) {
    sct.setEL(KeyConstants._localMode, AppListenerUtil.toLocalMode(lm, null));
}
```

This exposes the **resolved** localmode (including parent inheritance) in metadata, making it useful for debugging/introspection.

## Script Syntax

Script-based components (`component localmode="modern" { }`) are handled automatically via the TLD definition - no separate parser changes needed.

## Inheritance Resolution Order (final)

1. Function's own `localmode` attribute → use it
2. Owning component's `localmode` attribute → use it
3. Application.cfc `this.localmode` setting → use it
4. Server admin default → use it

## Testing Notes

- Component with `localmode="modern"` should force all child functions to use local scope
- Component with `localmode="classic"` should allow variables scope leakage
- Functions with explicit `localmode` should override component setting
- Extended components - child component should inherit parent's localmode if not specified

## Bytecode Impact

Adding one `int` parameter to the ComponentImpl constructor (15 → 16 args). **Backwards compatible** - the old 15-param constructor is kept and delegates to the new 16-param constructor with `localMode=-1` (inherit).

Old bytecode: calls 15-param constructor → delegates to 16-param with `-1` → works fine, inherits from Application.cfc as before.

New bytecode: calls 16-param constructor directly with explicit localMode value.

## Known Issues / Out of Scope

### Closures and Lambdas

Closures and lambdas extend `EnvUDF` which extends `UDFImpl`, and they have an `ownerComponent` reference. This means they WILL inherit component localmode through the `ownerComponent.getLocalMode()` check.

**This is arguably wrong** - closures capture their lexical scope at creation time and shouldn't be affected by the component they happen to be "owned" by. However, this is a pre-existing issue with how localmode works for closures/lambdas in general, not something introduced by LDEV-2919.

**Proper fix:** Implement an "outer scope" concept that captures the effective localmode at closure/lambda creation time. The inheritance chain for closures should be:

```text
Closure → Enclosing Function's localmode → Component → Application → Server
```

Not:

```text
Closure → ownerComponent → Application → Server
```

This would require capturing and storing the effective localmode when the closure is defined, rather than looking it up dynamically at call time through `ownerComponent`.

**Related tickets:**

- **LDEV-5913:** Fixed `super` method resolution in closures by introducing `getCurrentComponent()` which walks the component hierarchy based on `getCurrentPageSource()` to find the correct context
- **LDEV-5894:** Fixed nested closure context resolution (changed `if` to `while` loop for ClosureScope unwrapping in `PageContextImpl`)

These show there's already work being done on proper closure context resolution. A similar pattern could be used for localmode - using `getCurrentPageSource()` to find the correct component context rather than relying solely on `ownerComponent`.

**Recommendation:** Fix closure/lambda localmode inheritance as a separate ticket (outer scope implementation). For LDEV-2919, we follow the existing pattern. The fix could leverage patterns from LDEV-5913/5894.

### cfthread

Threads spawn with their own `PageContext`. Need to verify threads inherit localmode correctly from their spawning context. Not expected to be an issue but not explicitly tested.

### ORM Entities

ORM entities are components. They should respect component localmode like any other component. No special handling needed.
