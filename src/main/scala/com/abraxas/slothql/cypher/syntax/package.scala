package com.abraxas.slothql.cypher

import scala.language.{ higherKinds, implicitConversions }

import cats.data.Ior
import shapeless.{ <:!<, Generic, HList, |∨| }

import com.abraxas.slothql.cypher.CypherFragment.{ Expr, Known, Return }

package object syntax extends LowPriorityImplicits {

  sealed trait Graph extends Expr.Var[Map[String, Any]] with Graph.Vertex

  type GraphElem = Expr.Var[Map[String, Any]] with Graph.Elem
  type Vertex    = Expr.Var[Map[String, Any]] with Graph.Vertex
  type Edge      = Expr.Var[Map[String, Any]] with Graph.Edge

  object GraphElem {
    private[syntax] class Impl extends Expr.Var[Map[String, Any]] {
      private[syntax] var _alias: String = _ // This `var` should be set only once by a macro

      lazy val name: String = _alias
      lazy val m: Manifest[Map[String, Any]] = manifest[Map[String, Any]]
    }
  }

  final implicit class GraphElemOps(e: GraphElem) {
    /** Select vertex/edge property. */
    def prop[A: Manifest](k: String): Expr.Key[A] = Expr.Key[A](e, k)
    /** Select vertex/edge property as [[Option]]. */
    def propOpt[A: Manifest](k: String): Expr.Key[Option[A]] = prop[Option[A]](k)

    /** Alias for [[prop]]. */
    @deprecated("seems to break query type resolution", since = "03.06.18")
    def apply[A: Manifest](k: String): Expr.Key[A] = prop(k)
    /** Alias for [[propOpt]]. */
    def opt[A: Manifest](k: String): Expr.Key[Option[A]] = propOpt(k)

    /** Call built-in function `func` passing `this` expression as first argument. */
    def call[R: Manifest](func: String, args: Known[Expr[_]]*): Expr.Call[R] =
      Expr.Call(func, e.known :: args.toList)

    /** Call built-in `id` function. */
    def id: Expr.Call[Long] = call("id")
    /** Call built-in `count` function. */
    def count: Expr.Call[Long] = call("count")
    /** Call built-in `keys` function. */
    def keys: Expr.Call[List[String]] = call("keys")

  }

  final implicit class VertexOps(v: Vertex) {
    /** Call built-in `labels` function. */
    def labels: Expr.Call[List[String]] = v.call("labels")
  }

  final implicit class EdgeOps(e: Edge) {
    /** Call built-in `type` function. */
    def tpe: Expr.Call[String] = e.call("type")
    /** Call built-in `type` function. */
    def `type`: Expr.Call[String] = tpe
  }

  private[syntax] object Graph{
    sealed trait Elem
    sealed trait Vertex extends Elem
    sealed trait Edge   extends Elem

    val instance: Graph = new GraphElem.Impl with Graph {}
  }

  object Vertex {
    @inline private[syntax] def apply(): Vertex = (new GraphElem.Impl).asInstanceOf[Vertex]
    def unapplySeq(v: Vertex): Option[Seq[AnyRef]] = Some(???)
  }
  object Edge {
    @inline private[syntax] def apply(): Edge = (new GraphElem.Impl).asInstanceOf[Edge]
    def unapplySeq(v: Edge): Option[Seq[AnyRef]] = Some(???)
  }


  object := {
    def unapply(arg: Any): Option[(String, Any)] = Some(???)
  }

  object *: {
    def unapply(edge: Edge): Option[(Expr.Var[List[Map[String, Any]]], -[Int, Int], Edge)] = Some(???)
  }

  // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //

  implicit class CallExprOps(func: Symbol) {
    def call[R](args: Known[Expr[_]]*): Expr.Call[R] = Expr.Call(func.name, args.toList)
  }

  implicit class BooleanExprOps[E0 <: Expr[Boolean]: CypherFragment](expr0: E0) {
    def unary_! : Expr.LogicNegationExpr = Expr.LogicNegationExpr(expr0.known)

    def and[E1 <: Expr[Boolean]: CypherFragment](expr1: E1): Expr.LogicBinaryExpr = binary(expr1, Expr.LogicExpr.And)
    def or [E1 <: Expr[Boolean]: CypherFragment](expr1: E1): Expr.LogicBinaryExpr = binary(expr1, Expr.LogicExpr.Or)
    def xor[E1 <: Expr[Boolean]: CypherFragment](expr1: E1): Expr.LogicBinaryExpr = binary(expr1, Expr.LogicExpr.Xor)

    def &&[E1 <: Expr[Boolean]: CypherFragment](expr1: E1): Expr.LogicBinaryExpr = and(expr1)
    def ||[E1 <: Expr[Boolean]: CypherFragment](expr1: E1): Expr.LogicBinaryExpr = or (expr1)

    private def binary[E1 <: Expr[Boolean]: CypherFragment](expr1: E1, op: Expr.LogicExpr.BinaryOp) =
      Expr.LogicBinaryExpr(expr0.known, expr1.known, op)
  }

  implicit class CompareAnyOps[E0 <: Expr[_]: CypherFragment](expr0: E0) {
    def eq [E1 <: Expr[_]: CypherFragment](expr1: E1): Expr.CompareBinaryAnyExpr = binary(expr1, Expr.CompareExpr.Eq)
    def neq[E1 <: Expr[_]: CypherFragment](expr1: E1): Expr.CompareBinaryAnyExpr = binary(expr1, Expr.CompareExpr.Neq)

    def ===[E1 <: Expr[_]: CypherFragment](expr1: E1): Expr.CompareBinaryAnyExpr = eq(expr1)
    def <> [E1 <: Expr[_]: CypherFragment](expr1: E1): Expr.CompareBinaryAnyExpr = neq(expr1)

    def isNull  : Expr.CompareUnaryExpr = unary(Expr.CompareExpr.IsNull)
    def notNull : Expr.CompareUnaryExpr = unary(Expr.CompareExpr.NotNull)

    private def unary(op: Expr.CompareExpr.UnaryOp) = Expr.CompareUnaryExpr(expr0.known, op)
    private def binary[E1 <: Expr[_]: CypherFragment](expr1: E1, op: Expr.CompareExpr.BinaryAnyOp) =
      Expr.CompareBinaryAnyExpr(expr0.known, expr1.known, op)
  }

  implicit class CompareOps[A, E0[x] <: Expr[x]](expr0: E0[A])(implicit frag0: CypherFragment[E0[A]]) {
    def lt [E1 <: Expr[A]: CypherFragment](expr1: E1): Expr.CompareBinaryExpr[A] = binary(expr1, Expr.CompareExpr.Lt)
    def lte[E1 <: Expr[A]: CypherFragment](expr1: E1): Expr.CompareBinaryExpr[A] = binary(expr1, Expr.CompareExpr.Lte)
    def gte[E1 <: Expr[A]: CypherFragment](expr1: E1): Expr.CompareBinaryExpr[A] = binary(expr1, Expr.CompareExpr.Gte)
    def gt [E1 <: Expr[A]: CypherFragment](expr1: E1): Expr.CompareBinaryExpr[A] = binary(expr1, Expr.CompareExpr.Gt)

    def < [E1 <: Expr[A]: CypherFragment](expr1: E1): Expr.CompareBinaryExpr[A] = binary(expr1, Expr.CompareExpr.Lt)
    def <=[E1 <: Expr[A]: CypherFragment](expr1: E1): Expr.CompareBinaryExpr[A] = binary(expr1, Expr.CompareExpr.Lte)
    def >=[E1 <: Expr[A]: CypherFragment](expr1: E1): Expr.CompareBinaryExpr[A] = binary(expr1, Expr.CompareExpr.Gte)
    def > [E1 <: Expr[A]: CypherFragment](expr1: E1): Expr.CompareBinaryExpr[A] = binary(expr1, Expr.CompareExpr.Gt)

    def in[E1 <: Expr[List[A]]: CypherFragment](expr1: E1): Expr.In[A] = Expr.In(expr0.known, expr1.known)

    private def binary[E1 <: Expr[A]: CypherFragment](expr1: E1, op: Expr.CompareExpr.BinaryOp) =
      Expr.CompareBinaryExpr(expr0.known, expr1.known, op)
  }

  implicit class ListOps[A, E0 <: Expr[_]](expr0: E0)(implicit frag0: CypherFragment[E0], ev: E0 <:< Expr[List[A]]) {
    def concat[E1 <: Expr[List[A]]: CypherFragment](expr1: E1): Expr.Concat[A] =
      Expr.Concat(expr0.known.widen, expr1.known)
    def ++[E1 <: Expr[List[A]]: CypherFragment](expr1: E1): Expr.Concat[A] = concat(expr1)

    def at[I: (Int |∨| Long)#λ, E1[x] <: Expr[x]](i: E1[I])(implicit frag1: CypherFragment[E1[Long]]): Expr.AtIndex[A] =
      Expr.AtIndex(expr0.known.widen, i.asInstanceOf[E1[Long]].known)

    def at[E1 <: Expr[Long]: CypherFragment, E2 <: Expr[Long]: CypherFragment](range: Ior[E1, E2]): Expr.AtRange[A] =
      Expr.AtRange(expr0.known.widen, range.bimap(_.known, _.known))

    def at[I1: (Int |∨| Long)#λ, I2: (Int |∨| Long)#λ, E1[x] <: Expr[x], E2[x] <: Expr[x]](l: E1[I1], r: E2[I2])(
      implicit frag1: CypherFragment[E1[Long]], frag2: CypherFragment[E2[Long]]
    ): Expr.AtRange[A] =
      at(Ior.Both(l.asInstanceOf[E1[Long]], r.asInstanceOf[E2[Long]]))

    def from[I: (Int |∨| Long)#λ, E1[x] <: Expr[x]](i: E1[I])(implicit frag1: CypherFragment[E1[Long]]): Expr.AtRange[A] =
      at[E1[Long], E1[Long]](Ior.Left(i.asInstanceOf[E1[Long]]))

    def to[I: (Int |∨| Long)#λ, E1[x] <: Expr[x]](i: E1[I])(implicit frag1: CypherFragment[E1[Long]]): Expr.AtRange[A] =
      at[E1[Long], E1[Long]](Ior.Right(i.asInstanceOf[E1[Long]]))

  }

  // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // // //


  object -> {
    def unapply(g: Graph): Option[(Vertex, Vertex)] = Some(Vertex() -> Vertex())
    def unapply(v: Vertex): Option[(Vertex, Vertex)] = Some(Vertex() -> Vertex())
  }
  object `<-` {
    def unapply(g: Graph): Option[(Vertex, Vertex)] = Some(Vertex() -> Vertex())
    def unapply(v: Vertex): Option[(Vertex, Vertex)] = Some(Vertex() -> Vertex())
  }

  case class -[A, B](a: A, b: B)
//  object - {
//    def unapply(g: Graph): Option[(Vertex - Edge, Vertex)] = ???
//    def unapply(v: Vertex): Option[(Vertex - Edge, Vertex)] = ???
//  }

  object > {
    def unapply(g: Graph): Option[(Vertex - Edge, Vertex)] = Some(new -(Vertex(), Edge()) -> Vertex())
    def unapply(v: Vertex): Option[(Vertex - Edge, Vertex)] = Some(new -(Vertex(), Edge()) -> Vertex())

    def unapply(path: Vertex - Edge): Option[(Vertex - Edge, Vertex - Edge)] = Some(new -(Vertex(), Edge()) -> new -(Vertex(), Edge()))
  }
  object < {
    def unapply(g: Graph): Option[(Vertex, Edge - Vertex)] = Some(Vertex() -> new -(Edge(), Vertex()))
    def unapply(v: Vertex): Option[(Vertex, Edge - Vertex)] = Some(Vertex() -> new -(Edge(), Vertex()))
  }

  object -- {
    def unapply(g: Graph): Option[(Vertex, Vertex)] = Some(Vertex() -> Vertex())
    def unapply(v: Vertex): Option[(Vertex, Vertex)] = Some(Vertex() -> Vertex())
  }


  lazy val ⟶ : ->.type = ->
  lazy val ⟵ : `<-`.type = `<-`
  lazy val ⟷ : --.type = --


  implicit def lit[A: Manifest](a: A): Expr.Lit[A] = Expr.Lit[A](a)
  implicit def knownLit[A: Manifest](a: A)(implicit frag: CypherFragment[Expr.Lit[A]]): Known[Expr.Lit[A]] = Expr.Lit[A](a).known

  implicit def list[A](exprs: Known[Expr[A]]*): Expr.List[A] = Expr.List[A](exprs.toList)


  implicit def queryReturn[T](t: T)(implicit qr: QueryReturn[T]): qr.Out = qr(t)

  sealed trait QueryReturn[T]{
    type Ret
    type Out <: Return[Ret]
    def apply(t: T): Out
  }
  object QueryReturn {
    type Aux[T, R, O <: Return[R]] = QueryReturn[T] { type Ret = R; type Out = O }

    implicit def returnExpr[A, E <: Expr[_]](
      implicit
      ev: E <:< Expr.Inv[A],
      fragment: CypherFragment[E]
    ): Aux[E, A, Return.Expr[A]] =
      new QueryReturn[E] {
        type Ret = A
        type Out = Return.Expr[A]
        def apply(e: E): Return.Expr[A] = Return.Expr(Known(e).widen, as = None)
      }

    implicit def returnTuple[P <: Product, L <: HList](
      implicit
      ev: P <:!< Expr[_],
      gen: Generic.Aux[P, L],
      build: Return.List.Build[L]
    ): Aux[P, build.Ret, build.Out] =
      new QueryReturn[P] {
        type Ret = build.Ret
        type Out = build.Out
        def apply(p: P): build.Out = build(gen.to(p))
      }

    implicit def returnOptions[A, E <: Return.Options[_]](implicit ev: E <:< Return.Options.Inv[A]): Aux[E, A, Return.Options[A]] =
      _retOptions.asInstanceOf[Aux[E, A, Return.Options[A]]]
    private lazy val _retOptions = new QueryReturn[Return.Options[_]] {
      type Ret = Any
      type Out = Return.Options[_]
      def apply(t: Return.Options[_]): Return.Options[_] = t
    }
  }


  implicit class ReturnOps[E](ret: E) {
    def orderBy(by: ReturnOps.OrderBy*)(implicit qr: QueryReturn[E]): Return.Options[qr.Ret] = {
      val order = by.map(_.asPair).toMap
      qr(ret) match {
        case ops:  Return.Options[qr.Ret @unchecked] => ops.copy(order = ops.order ++ order)
        case expr: Return.Return0[qr.Ret @unchecked] => Return.Options(expr, order, None, None)
      }
    }
  }

  object ReturnOps {
    sealed trait OrderBy{
      val expr: Known[Expr[_]]
      def isAscending: Boolean = this.isInstanceOf[Ascending]
      def asPair: (Known[Expr[_]], Boolean) = expr -> isAscending
    }
    case class Ascending(expr: Known[Expr[_]]) extends OrderBy
    case class Descending(expr: Known[Expr[_]]) extends OrderBy

    implicit def defaultOrderingIsAscending[E <: Expr[_]](e: E)(implicit frag: CypherFragment[E]): Ascending = Ascending(e)
  }

  implicit class OrderDescendingOps[E <: Expr[_]: CypherFragment](e: E) {
    def desc: ReturnOps.Descending = ReturnOps.Descending(e)
  }
}

trait LowPriorityImplicits {
  implicit def unwrapBooleanExprInIfGuard(e: Expr[Boolean]): Boolean = ???
}
