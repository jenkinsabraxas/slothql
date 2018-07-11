package com.abraxas.slothql.mapper

import scala.language.higherKinds

import cats.syntax.flatMap._
import cats.syntax.functor._
import shapeless._
import shapeless.tag.@@

sealed trait FuncArrow extends Arrow
final class Func1Arrow[S, T](func: S => T) extends (S => T) with FuncArrow {
  type Source = S
  type Target = T
  @inline def apply(s: S): T = func(s)
  override def toString() = "<function1>"
}

object Func1Arrow {
  type Aux[-S, +T] = Func1Arrow[_, _] { type Source >: S; type Target <: T }
  def apply[S, T](func: S => T): Func1Arrow[S, T] = new Func1Arrow(func)

  implicit def composeFunc1ArrowsAsFunctions[A, B, C]: Arrow.Compose.Aux[Func1Arrow[B, C], Func1Arrow[A, B], Func1Arrow[A, C]] =
    composeInstance.asInstanceOf[Arrow.Compose.Aux[Func1Arrow[B, C], Func1Arrow[A, B], Func1Arrow[A, C]]]
  private lazy val composeInstance = new Arrow.Compose[Func1Arrow[Any, Any], Func1Arrow[Any, Any]] {
    type Out = Func1Arrow.Aux[Any, Any]
    def apply(f: Func1Arrow[Any, Any], g: Func1Arrow[Any, Any]): Func1Arrow.Aux[Any, Any] = Func1Arrow(f compose g)
  }
}

object FuncArrow {
  def apply[A <: Arrow](a: A)(implicit f: Functor[A, FuncArrow]): f.Out = f(a)


  object ApplyTupled extends Poly1 {
    implicit def applyFunc1Arrow[S, T]: Case.Aux[(Func1Arrow[S, T], S), T] = at[(Func1Arrow[S, T], S)]{ case (f, s) => f(s) }
  }

  // TODO: import is required
  implicit def mapSplitToFunc1Arrow[
    A <: Arrow, Arrows <: HList, A1 <: Arrow, Arrows1 <: HList, S, T, N <: Nat, ZL <: HList, TL <: HList
  ](
    implicit
    isSplit0: A <:< Arrow.Split[Arrows],
    fmap: Functor.Aux[A, FuncArrow, A1],
    isSplit1: A1 <:< Arrow.Split.Aux[Arrows1, S, T],
    zip: ops.hlist.ZipConst.Aux[S, Arrows1, ZL],
    applyArrows: ops.hlist.Mapper.Aux[ApplyTupled.type, ZL, TL],
    tupler: ops.hlist.Tupler.Aux[TL, T]
  ): Functor.Aux[A, FuncArrow, Func1Arrow[S, T]] =
    new Functor[A, FuncArrow] {
      type Out = Func1Arrow[S, T]
      def apply(t: A): Func1Arrow[S, T] = Func1Arrow(s => tupler(applyArrows(zip(s, isSplit1(fmap(t)).arrows)))
      )
    }

  implicit def mapScalaExprIdToFunc1Arrow[A]: Functor.Aux[ScalaExpr.Id[A], FuncArrow, Func1Arrow[A, A]] =
    scalaExprIdFunctor.asInstanceOf[Functor.Aux[ScalaExpr.Id[A], FuncArrow, Func1Arrow[A, A]]]
  private lazy val scalaExprIdFunctor = new Functor[Arrow, FuncArrow] {
    type Out = Func1Arrow[Any, Any]
    def apply(t: Arrow): Func1Arrow[Any, Any] = Func1Arrow(identity[Any])
  }

  implicit def mapScalaExprSelectFieldToFunc1Arrow[Obj, K <: String, V, Repr <: HList](
    implicit
    generic: Cached[LabelledGeneric.Aux[Obj, Repr]],
    select:  Cached[ops.record.Selector.Aux[Repr, Symbol @@ K, V]]
  ): Functor.Aux[ScalaExpr.SelectField[Obj, K, V], FuncArrow, Func1Arrow[Obj, V]] =
    new Functor[ScalaExpr.SelectField[Obj, K, V], FuncArrow] {
      type Out = Func1Arrow[Obj, V]
      def apply(t: ScalaExpr.SelectField[Obj, K, V]): Func1Arrow[Obj, V] =
        Func1Arrow(select.value.apply _ compose generic.value.to)
    }

  implicit def mapScalaExprFMapToFunc1Arrow[F[_], E <: ScalaExpr, S, T](
    implicit
    functor: cats.Functor[F],
    types0: Arrow.Types.Aux[E, S, T],
    mapE: Functor.Aux[E, FuncArrow, Func1Arrow[S, T]]
  ): Functor.Aux[ScalaExpr.FMap[F, E], FuncArrow, Func1Arrow[F[S], F[T]]] =
    new Functor[ScalaExpr.FMap[F, E], FuncArrow] {
      type Out = Func1Arrow[F[S], F[T]]
      def apply(t: ScalaExpr.FMap[F, E]): Func1Arrow[F[S], F[T]] = Func1Arrow(_.map(mapE(t.expr)))
    }

  implicit def mapScalaExprMBindToFunc1Arrow[F[_], E <: ScalaExpr, S, T, T0](
    implicit
    monad: cats.Monad[F],
    types0: Arrow.Types.Aux[E, S, T],
    mapE: Functor.Aux[E, FuncArrow, Func1Arrow[S, T]],
    targetIsF: T <:< F[T0]
  ): Functor.Aux[ScalaExpr.MBind[F, E], FuncArrow, Func1Arrow[F[S], F[T0]]] =
    new Functor[ScalaExpr.MBind[F, E], FuncArrow] {
      type Out = Func1Arrow[F[S], F[T0]]
      def apply(t: ScalaExpr.MBind[F, E]): Func1Arrow[F[S], F[T0]] = Func1Arrow(_ flatMap mapE(t.expr).andThen(targetIsF))
    }

//  implicit def mapScalaExpr?ToFunc1Arrow: Functor.Aux[ScalaExpr.?, FuncArrow, Func1Arrow[?, ?]] = ???

}

