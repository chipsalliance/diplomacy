# Diplomacy
Standalone implementation to diplomacy.

Diplomacy is a set of abstractions for describing directed, acyclic graphs where parameters will be negotiated between nodes. These abstractions are expressed in the form of abstract classes, traits, and type parameters, which comprises nearly all of the types defined in this package.

# Three Part of diplomacy

## CDE(context-dependent-environments)
CDE is A Scala library for Context-Dependent Environments, where a key-value environment is passed down a module hierarchy and each returned value depends on the query’s origin as well as the key.
CDE is provably superior to existing parameterization schemes because it avoids introducing non-local source code changes when a design is modified, while also enabling features for large-scale design space exploration of compositions of generators.

## LazyModule
LazyModule builds on top of the DAG annotated with the negotiated parameters and leverage's Scala's lazy evaluation property to split Chisel module generation into two phases:

## Nodes
The [[NodeImp]] ("node implementation") is the main abstract type that associates the type parameters of all other abstract types. Defining a concrete implementation of [[NodeImp]] will therefore determine concrete types for all type parameters. For example, passing in a concrete instance of NodeImp to SourceNode will fully determine concrete types for all of a SourceNode's type parameters.

# TODOs
- [x] Split from rocket-chip and compile with https://github.com/sequencer/chipsalliance-playground  
- [ ] absorb [AOP](https://github.com/chipsalliance/rocket-chip/blob/master/src/main/scala/aop/Select.scala)  
- [ ] Publish as standalone JAR.  
- [x] RocketChip test pass with `chisel3.diplomacy` as dependency.
- [ ] unittest for CDE.  
- [ ] unittest for LazyModule.  
- [ ] unittest for Nodes.  
- [ ] scaladoc website.  
- [ ] release CI.  
- [ ] chipsalliance members review.  
- [ ] upstream to chipsallinace.  
- [ ] upstream as chipsalliance/rocket-chip as its dependency.  
