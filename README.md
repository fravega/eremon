Easy REactive MONgo ( EREMON )
======================

This project is build on top of [ReactiveMongo](https://github.com/ReactiveMongo/ReactiveMongo) to provide an easy "DAO"/"Repository" oriented solution. I use the Criteria DSL from [ReactiveMongo-Criteria](https://github.com/osxhacker/ReactiveMongo-Criteria)

## Quick start

### SBT

In your `build.sbt`, add the following entries:

```scala
resolvers += Resolver.bintrayRepo("jarlakxen", "maven")

libraryDependencies += "org.reactivemongo" %% "reactivemongo" % "0.12.6"
libraryDependencies += "io.eremon" %% "eremon-core" % "1.8.0"
```

### Repository

#### Define a repository

```scala
  import io.eremon._
  import reactivemongo.api.indexes._
  import reactivemongo.bson._
  import reactivemongo.bson.Macros.Annotations._

   case class User(name: String, age: Int, software: Set[String], @Key("_id") id: ID = ID.generate())

   implicit val userReader: BSONDocumentReader[User] = Macros.reader[User]
   implicit val userWriter: BSONDocumentWriter[User] = Macros.writer[User]

   class UserRepository(database: MongoDB)(implicit ec: ExecutionContext) extends ReactiveRepository[User](
     database,
     "User",
     testReader,
     testWriter,
     ec) {

       override def indexes: Seq[Index] = Seq(
          Index(Seq("name" -> IndexType.Ascending), unique = true)
        )
      }

   }
```

#### Create a new instance of a repository
```scala
  import io.eremon._
  import scala.concurrent.ExecutionContext.Implicits.global
  
  val mongoUri = s"mongodb://<host>:<port>/test-db"
  
  val database: MongoDB = MongoConnector(mongoUri)
  
  val repository = new UserRepository(database)
```

#### Methods

```scala
  val id = ID.generate()
  val entity = Test("Linus Torvalds", 47, Set("Linux"), id)
  
  repository.insert(entity)
  repository.findById(id)
  repository.findAll()
  repository.removeById(id)
  repository.findOne(criteria[User](_.name) === "Linus Torvalds")
  repository.count
  repository.updateById(id, entity.copy(age = 46))
  repository.updateBy(criteria[User](_.name) === "Linus Torvalds", entity)
```

See more example in the [tests](https://github.com/fravega/eremon/tree/master/core/src/test/scala/io/eremon).

### Criteria DSL

#### Untyped DSL Support

What the DSL *does* provide is the ablity to formulate queries thusly:

```scala
  import io.eremon.criteria._
  ...
  // Using an Untyped.criteria
  {
  import Untyped._

  // The MongoDB properties referenced are not enforced by the compiler
  // to belong to any particular type.  This is what is meant by "Untyped".
  val adhoc = criteria.firstName === "Jack" && criteria.age >= 18;
  val cursor = collection.find(adhoc).cursor[BSONDocument];
  }
```

Another form which achieves the same result is to use one of the `where` methods available:

```scala
  import io.eremon.criteria._
  ...
  // Using one of the Untyped.where overloads
  {
  import Untyped._

  val cursor = collection.find(
    where (_.firstName === "Jack" && _.age >= 18)
	).cursor[BSONDocument];
  }
```

There are overloads for between 1 and 22 place holders using the `where` method.  Should more than 22 be needed, then the 1 argument version should be used with a named parameter.  This allows an infinite number of property constraints to be specified.

#### Typed DSL Support

For situations where the MongoDB document structure is well known and a developer wishes enforce property existence **during compilation**, the `Typed` Criteria can be used:

```scala
  import io.eremon.criteria._
  ...
  {
  // Using a Typed criteria which restricts properties to those
  // within a given type and/or those directly accessible
  // through property selectors.
  import Typed._

  case class Nested (rating : Double)
  case class ExampleDocument (aProperty : String, another : Int, nested : Nested)

  val byKnownProperties = criteria[ExampleDocument] (_.aProperty) =~ "^[A-Z]\\w+" && (
    criteria[ExampleDocument] (_.another) > 0 ||
    criteria[ExampleDocument] (_.nested.rating) < 10.0
	);

  val cursor = collection.find(byKnownProperties).cursor[BSONDocument];
  }
```

When the `Typed` version is employed, compilation will fail if the provided property navigation does not exist from the *root type* (specified as the type parameter to `criteria` above) **or** the leaf type is not type-compatible with the value(s) provided (if any).

An easy way to think of this is that if it doesn't compile in "regular usage", then it definitely will not when used in a `Typed.criteria`.


### Usage Considerations

Note that `Typed` and `Untyped` serve different needs.  When the structure of a document collection is both known *and* identified as static, `Typed` makes sense to employ.  However, `Untyped` is compelling when document structure can vary within a collection.  These are considerations which can easily vary between projects and even within different modules of one project.

Feel free to use either or both `Typed` and `Untyped` as they make sense for the problem at hand.  One thing to keep in mind is that the examples shown above assumes only one is in scope.


## Operators

When using the Criteria DSL, the fact that the operators adhere to the expectations of both programmers and Scala precedences, most uses will "just work."  For example, explicitly defining grouping is done with parentheses, just as you would do with any other bit of Scala code.

For the purposes of the operator API reference, assume the following code is in scope:

```scala
import io.eremon.criteria.Untyped._
```

### Comparison Operators

With the majority of comparison operators, keep in mind that the definition of their ordering is dependent on the type involved.  For example, strings will use lexigraphical ordering whereas numbers use natural ordering.

* **===**, **@==** Matches properties based on value equality.

```scala
criteria.aProperty === "value"
```

```scala
criteria.aProperty @== "value"
```

* **<>**, **=/=**, **!==** Matches properties which do not have the given value.

```scala
criteria.aProperty <> "value"
```

```scala
criteria.aProperty =/= "value"
```

```scala
criteria.aProperty !== "value"
```

* **<** Matches properties which compare "less than" a given value.

```scala
criteria.aNumber < 99
```

* **<=** Matches properties which compare "less than or equal to" a given value.

```scala
criteria.aNumber <= 99
```

* **>** Matches properties which compare "greater than" a given value.

```scala
criteria.aProperty > "Alice"
```

* **>=** Matches properties which compare "greater than or equal to" a given value.

```scala
criteria.aNumber >= 100
```

### Existence Operators

* **exists** Matches any document which has the specified field.  Use the unary not operator to match based on the leaf property being absent entirely.

```scala
criteria.aProperty.exists	// Requires 'aProperty' to be in the document
!criteria.aProperty.exists	// Only matches documents without 'aProperty'
```

* **in** Matches properties which equal one of the given values or array properties having one element which equals any of the given values.  Combine with the unary not operator to specify "not in."

```scala
criteria.ranking.in (1, 2, 3, 4, 5)
!criteria.ranking.in (1, 2, 3, 4, 5)
```

* **all** Matches array properties which contain all of the given values.

```scala
criteria.strings.all ("hello", "world")
```

### String Operators

* **=~** Matches a string property which satisfies the given regular expression `String`, optionally with [regex flags](https://docs.mongodb.com/manual/reference/operator/query/regex/).

```scala
criteria.aProperty =~ """^(value)|(someting\s+else)"""
criteria.aProperty =~ """^(value)|(someting\s+else)""" -> IgnoreCase
```

* **!~** Matches a string property which does _not_ satisfy the given regular expression `String`, optionally with [regex flags](https://docs.mongodb.com/manual/reference/operator/query/regex/).

```scala
criteria.aProperty !~ """\d+"""
criteria.aProperty !~ """foo.*bar""" -> (IgnoreCase | MultilineMatching)
```

### Logical Operators

* **!** The unary not operator provides logical negation of an `Expression`.

```scala
!(criteria.aProperty === "value")
```

* **&&** Defines logical conjunction (''AND'').

```scala
criteria.aProperty === "value" && criteria.another > 0
```

* **!&&** Defines negation of conjunction (''NOR'').

```scala
criteria.aProperty === "value" !&& criteria.aProperty @== "other value"
```

* **||** Defines logical disjunction (''OR'').

```scala
criteria.aProperty === "value" || criteria.aProperty === "other value"
```
