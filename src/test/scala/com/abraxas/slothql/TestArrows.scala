package com.abraxas.slothql

import scala.language.higherKinds

import com.abraxas.slothql.mapper._




object Functors {
/*
  implicit def fieldFocusToDBArrowLeaf[
    A <: Arrow, Obj, K <: Symbol, V, Repr <: GraphRepr.Element, Fields <: HList, Field <: GraphRepr.Property, V0
  ](
    implicit
    arr: A <:< FieldFocus[Obj, K, V],
    schema: Schema.Aux[Obj, Repr],
    node: Repr <:< GraphRepr.Node.Aux[_, Fields, _],
    select: shapeless.ops.record.Selector.Aux[Fields, K, Field],
    prop: Field <:< GraphRepr.Property.Aux[V0],
    ev: V <:< V0
  ): Functor.Aux[A, GraphPath, PropSelection[Repr, Field]] =
    Functor.define[A, GraphPath](_ => PropSelection(schema.repr, select(schema.repr.Fields)))

  implicit def fieldAndSeqFocusToDBArrow[
    A <: Arrow, AField <: Arrow, ASeq <: Arrow,
    Obj, K <: Symbol, V, Repr <: GraphRepr.Node,
    CC[x] <: Seq[x], V0,
    Rels <: HList, Rel <: GraphRepr.Relation, RelFields <: HList, RelTarget <: GraphRepr.Node,
    IndexField, IndexProp <: GraphRepr.Property, Index
  ](
    implicit
    arr: A <:< Arrow.Composition[ASeq, AField],
    fieldArr: AField <:< FieldFocus[Obj, K, V],
    seqArr: ASeq <:< SeqFocus[CC, V0],
    seq: V <:< CC[V0],
    schema: Schema.Aux[Obj, Repr],
    node: Repr <:< GraphRepr.Node.Aux[_, _, Rels],
    select: shapeless.ops.record.Selector.Aux[Rels, K, Rel],
    outgoing: Rel <:< GraphRepr.Relation.Aux[_, RelFields, _, RelTarget],
    onlyIndex: shapeless.ops.hlist.IsHCons.Aux[RelFields, IndexField, HNil],
    indexProp: IndexField <:< Witness.`'index`.Field[IndexProp],
    index: IndexProp <:< GraphRepr.Property.Aux[Index],
    integralIndex: Integral[Index],
    compose: Compose[RelationTarget[Rel, RelTarget], OutgoingRelation[Repr, Rel]]
   ): Functor.Aux[A, GraphPath, compose.Out] =
    Functor.define[A, GraphPath]{ _ =>
      val rel = select(schema.repr.Outgoing.asInstanceOf[Rels])
      compose(RelationTarget(rel, rel.To.asInstanceOf[RelTarget]), OutgoingRelation(schema.repr, rel))
    }
*/
}



object FunctorsTest {
  import cats.instances.list._
  import cats.instances.option._

  import com.abraxas.slothql.test.models.{ Book, Page }

  val selPages = ScalaExpr[Book].pages
  // ScalaExpr.SelectField[Book, pages, List[Page]]
  // = SelectField("pages")

  val selText  = ScalaExpr[Page].text
  // ScalaExpr.SelectField[Page, text, String]
  // = SelectField("text")

  val mapPagesText1 = selPages.map(selText)
  // Arrow.Composition[
  //  ScalaExpr.FMap[List, ScalaExpr.SelectField[Page, text, String]],
  //  ScalaExpr.SelectField[Book, pages, List[Page]]
  // ]{type Source = Book;type Target = List[String]}
  // = FMap(SelectField(text)) ∘ SelectField(pages)

  val mapPagesText2 = selPages.map(_.text)
  // Arrow.Composition[
  //  ScalaExpr.FMap[List, ScalaExpr.SelectField[Page, text, String]],
  //  ScalaExpr.SelectField[Book, pages, List[Page]]
  // ]{type Source = Book;type Target = List[String]}
  // = FMap(SelectField(text)) ∘ SelectField(pages)

  val selAuthor = ScalaExpr[Book].selectDynamic("author") // same as `.author`
  // ScalaExpr.SelectField[Book, author, Option[Author]]
  // = SelectField("author")

  val mapAuthorName = selAuthor.map(_.name)
  // Arrow.Composition[
  //  ScalaExpr.FMap[Option, ScalaExpr.SelectField[Author, name, String]],
  //  ScalaExpr.SelectField[Book, author, Option[Author]]
  // ]{type Source = Book;type Target = Option[String]}
  // = FMap(SelectField(name)) ∘ SelectField(author)

  val mapAuthorPseudonym = selAuthor.flatMap(_.pseudonym)
  // Arrow.Composition[
  //  ScalaExpr.MBind[Option, ScalaExpr.SelectField[Author, pseudonym, Option[String]]],
  //  ScalaExpr.SelectField[Book, author, Option[Author]]
  // ]{type Source = Book;type Target = Option[String]}
  // = MBind(SelectField(pseudonym)) ∘ SelectField(author)

  val selIsbn = ScalaExpr[Book].meta.isbn
  // Arrow.Composition[
  //  ScalaExpr.SelectField[Meta, isbn, String],
  //  ScalaExpr.SelectField[Book, meta, Meta]
  // ]{type Source = Book;type Target = String}
  // = SelectField(isbn) ∘ SelectField(meta)

//  val mapped0 = Functor.map(sel2 ∘ sel1).to[GraphPath]
//  val mapped1 = Functor.map(sel3 ∘ (sel2 ∘ sel1)).to[GraphPath]

//  val m  = Functor.map(sel3).to[GraphPath] ∘ Functor.map(sel2 ∘ sel1).to[GraphPath]
//  assert(mapped1 == m)
}