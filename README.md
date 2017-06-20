# xjc-plugins-base
Aim of this framework is to speed up XJC plugins development.
It will also contains a few common fully usable plugins as a template for other contributors.

### usage of collection-manipulator
Add xml namespace to xjb document:
```
xmlns:coll="http://common.jaxb.devontrain.com/plugin/collection-manipulator"
```
Now you can change various things regarding your collection:
- name of the collection
- if setter should be generated or not
- Interface\class used as field type for collection property
- default implementation for lazy getters

```
<coll:mod
      name="hackers"
      setter="true"
      iface="java.util.Collection"
      impl="java.util.LinkedHashSet"
      />
```