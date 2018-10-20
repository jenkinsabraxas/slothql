package com.abraxas.slothql.arrow

import scala.annotation.implicitNotFound
import scala.language.experimental.macros
import scala.reflect.macros.whitebox

import shapeless._

trait Arrow {
  type Source
  type Target
}

object Arrow {
  type Aux[-S, +T] = Arrow { type Source >: S; type Target <: T }
  type Inv[ S,  T] = Arrow { type Source  = S; type Target  = T }


  // TODO: `Arrow.Id[Int] == Arrow.Id[String]` complies
  trait Id[A] extends Arrow {
    type Source = A
    type Target = A

    override def toString: String = "Id"
  }
  private object Id extends Id[Any]
  def Id[A]: Id[A] = Id.asInstanceOf[Id[A]]


  // case class Obj[A ](get: A) extends Arrow { type Source = A;    type Target =  A }
  // case class Lit[+A](get: A) extends Arrow { type Source = Unit; type Target <: A }

  trait Split[Arrows <: HList] extends Arrow {
    val arrows: Arrows

    override def toString: String = s"Split($arrows)"
  }
  object Split extends ProductArgs {
    type Aux[Arrows <: HList, S, T] = Split[Arrows] { type Source = S; type Target = T }

    def applyProduct[Arrows <: HList](arrows: Arrows)(implicit split: Splitter[Arrows]): split.Out = split(arrows)

    trait Splitter[Arrows <: HList] extends DepFn1[Arrows] { type Out <: Split[Arrows] }
    object Splitter {
      type Aux[Arrows <: HList, Ts <: Split[_]] = Splitter[Arrows] { type Out = Ts }

      implicit def arrowSplitter[Arrows <: HList, S, T](
        implicit
        canSplit: CanSplit.Aux[Arrows, S, T],
        lowPriority: LowPriority
      ): Splitter.Aux[Arrows, Split.Aux[Arrows, S, T]] = instance.asInstanceOf[Splitter.Aux[Arrows, Split.Aux[Arrows, S, T]]]

      private lazy val instance = new Splitter[HList] {
        type Out = Split.Aux[HList, Any, Any]
        def apply(t: HList): Out =
          new Split[HList] {
            type Source = Any
            type Target = Any
            val arrows: HList = t
          }
      }

      trait CanSplit[Arrows <: HList] {
        type Source
        type Target
      }
      object CanSplit{
        type Aux[Arrows <: HList, S, T] = CanSplit[Arrows] { type Source = S; type Target = T }

        implicit def proveCanSplit[Arrows <: HList, S, Ts <: HList, T](
          implicit
          source: CommonSource.Aux[Arrows, S],
          targets: Targets.Aux[Arrows, Ts],
          tupler: ops.hlist.Tupler.Aux[Ts, T]
        ): CanSplit.Aux[Arrows, S, T] = instance.asInstanceOf[CanSplit.Aux[Arrows, S, T]]

        private lazy val instance = new CanSplit[HList] {}
      }
    }
  }

  implicit class SplitOps[F <: Arrow](protected val f: F) {
    /**
     *  Warning: any macro application error in the arguments (like incorrect field name) will be swallowed by this macro,
     *  showing just `exception during macro expansion` error. Looks like [[https://github.com/scala/bug/issues/9889]].
     */
    def split(arrows: (F => Arrow)*): Arrow = macro SplitOps.splitImpl[F]
  }
  object SplitOps {
    def splitImpl[F: c.WeakTypeTag](c: whitebox.Context)(arrows: c.Tree*): c.Tree = {
      import c.universe._

      val f = c.prefix.tree match {
        case q"new $clazz($param)" if clazz.tpe <:< typeOf[SplitOps[_]] => param
        case q"arrow.this.Arrow.SplitOps[$_]($param)" => param
        case other => c.abort(c.prefix.tree.pos, s"Unexpected: $other")
      }
      q"""
        _root_.com.abraxas.slothql.arrow.Arrow.Split(..${arrows.map(t => q"$t($f)")})
      """
    }
  }


  /** An arrow that represents arrows composition. */
  trait Composition[F <: Arrow, G <: Arrow] extends Arrow {
    val F: F
    val G: G
    type Source >: G.Source
    type Target <: F.Target

    override def hashCode(): Int = (F.##, G.##).##
    override def equals(o: scala.Any): Boolean = PartialFunction.cond(o) {
      case that: Composition[_, _] => this.F == that.F && this.G == that.G
    }

    override def toString: String = s"$F ∘ $G"
  }
  object Composition {
    type Aux[F <: Arrow, G <: Arrow, S, T] = Composition[F, G] { type Source = S; type Target = T }
    def apply[F <: Arrow, G <: Arrow](f: F, g: G)(implicit compose: Compose[F, G]): compose.Out = compose(f, g)
  }

  /** Syntax sugar for arrows composition. */
  implicit class ComposeOps[F <: Arrow](f: F) {
    def compose[G <: Arrow](g: G)(implicit compose: Compose[F, G]): compose.Out = compose(f, g)
    def ∘      [G <: Arrow](g: G)(implicit compose: Compose[F, G]): compose.Out = compose(f, g)
    def <<<    [G <: Arrow](g: G)(implicit compose: Compose[F, G]): compose.Out = compose(f, g)

    def andThen[G <: Arrow](g: G)(implicit compose: Compose[G, F]): compose.Out = compose(g, f)
    def >>>    [G <: Arrow](g: G)(implicit compose: Compose[G, F]): compose.Out = compose(g, f)

    def andThen[G <: Arrow](fg: F => G)(implicit compose: Compose[G, F]): compose.Out = compose(fg(f), f)
    def >>>    [G <: Arrow](fg: F => G)(implicit compose: Compose[G, F]): compose.Out = compose(fg(f), f)
  }

  /** Syntax sugar for unchaining composed arrows. */
  implicit class UnchainOps[F <: Arrow](f: F) {
    def unchain(implicit ev: Unchain[F]): ev.Out = ev(f)
    def unchainRev[L <: HList](implicit ev: Unchain.Aux[F, L], reverse: ops.hlist.Reverse[L]): reverse.Out = reverse(ev(f))
  }



  /** Typeclass witnessing `Source` and `Target` of an [[Arrow]]. */
  trait Types[F <: Arrow]  { type Source; type Target }
  object Types {
    type Aux[F <: Arrow, S, T] = Types[F] { type Source = S; type Target = T }
    def apply[F <: Arrow](implicit t: Types[F]): Aux[F, t.Source, t.Target] = t

    implicit def arrowTypes[F <: Arrow, S, T](implicit ev: F <:< Arrow.Inv[S, T]): Aux[F, S, T] = instance.asInstanceOf[Aux[F, S, T]]
    private lazy val instance = new Types[Arrow] {}
  }

  /** Typeclass witnessing that arrows `F` and `G` can be composed. */
  trait Compose[F <: Arrow, G <: Arrow] extends DepFn2[F, G] { type Out <: Arrow }
  object Compose {
    type Aux[F <: Arrow, G <: Arrow, Composition <: Arrow] = Compose[F, G] { type Out = Composition }
    def apply[F <: Arrow, G <: Arrow](implicit compose: Compose[F, G]): Aux[F, G, compose.Out] = compose

    implicit def composeIdLeft[F <: Arrow, G <: Arrow, T](
      implicit
      types: Types.Aux[G, _, T],
      idF: F <:< Arrow.Id[T]
    ): Compose.Aux[F, G, G] =
      composeIdL.asInstanceOf[Compose.Aux[F, G, G]]
    private lazy val composeIdL = new Compose[Arrow.Id[Any], Arrow] {
      type Out = Arrow
      def apply(f: Arrow.Id[Any], g: Arrow): Arrow = g
    }

    implicit def composeIdRight[F <: Arrow, G <: Arrow, S](
      implicit
      types: Types.Aux[F, S, _],
      idG: G <:< Arrow.Id[S]
    ): Compose.Aux[F, G, F] =
      composeIdR.asInstanceOf[Compose.Aux[F, G, F]]
    private lazy val composeIdR = new Compose[Arrow, Arrow.Id[Any]] {
      type Out = Arrow
      def apply(f: Arrow, g: Arrow.Id[Any]): Arrow = f
    }


    implicit def canCompose[F <: Arrow, G <: Arrow, S, T](
      implicit
      typesCorrespond: TypesCorrespond.Aux[F, G, S, T],
      lowPriority: LowPriority
    ): Compose.Aux[F, G, Composition.Aux[F, G, S, T]] = instance.asInstanceOf[Aux[F, G, Composition.Aux[F, G, S, T]]]
    private lazy val instance = new Compose[Arrow, Arrow] {
      type Out = Composition[Arrow, Arrow]
      def apply(f: Arrow, g: Arrow) = new Composition[Arrow, Arrow] { val F = f; val G = g }
    }

    @implicitNotFound("Types of ${F} and ${G} do not correspond for composition")
    trait TypesCorrespond[F <: Arrow, G <: Arrow] {
      type Source
      type Target
    }
    object TypesCorrespond {
      type Aux[F <: Arrow, G <: Arrow, S, T] = TypesCorrespond[F, G] { type Source = S; type Target = T }

      implicit def proveTypesCorrespond[F <: Arrow, G <: Arrow, FS, FT, GS, GT](
        implicit
        fTypes: Types.Aux[F, FS, FT],
        gTypes: Types.Aux[G, GS, GT],
        typesCorrespond: FS <:< GT
      ): TypesCorrespond.Aux[F, G, GS, FT] = instance.asInstanceOf[TypesCorrespond.Aux[F, G, GS, FT]]
      private lazy val instance = new TypesCorrespond[Arrow, Arrow] {}
    }
  }


  /** Typeclass supporting extraction of composed arrows as an HList. */
  trait Unchain[F <: Arrow] extends DepFn1[F] { type Out <: HList }
  object Unchain {
    type Aux[F <: Arrow, Arrows <: HList] = Unchain[F] { type Out = Arrows }
    def apply[F <: Arrow](implicit unchain: Unchain[F]): Aux[F, unchain.Out] = unchain

    implicit def unchainComposition[C <: Composition[_, _], F <: Arrow, G <: Arrow, ChF <: HList, ChG <: HList](
      implicit
      ev: C <:< Composition[F, G],
      unchainF: Lazy[Unchain.Aux[F, ChF]],
      unchainG: Lazy[Unchain.Aux[G, ChG]],
      concat: ops.hlist.Prepend[ChF, ChG]
    ): Unchain.Aux[C, concat.Out] =
      new Unchain[C] {
        type Out = concat.Out
        def apply(c: C): Out =
          concat(unchainF.value(c.F.asInstanceOf[F]), unchainG.value(c.G.asInstanceOf[G]))
      }

    implicit def notChained[F <: Arrow](implicit ev: F <:!< Composition[_, _]): Unchain.Aux[F, F :: HNil] =
      _notChained.asInstanceOf[Unchain.Aux[F, F :: HNil]]
    private lazy val _notChained = new Unchain[Arrow] {
      type Out = Arrow :: HNil
      def apply(t: Arrow): Out = t :: HNil
    }
  }

  @implicitNotFound("Arrows ${Arrows} have different sources")
  trait CommonSource[Arrows <: HList] { type Source }
  object CommonSource {
    type Aux[Arrows <: HList, S] = CommonSource[Arrows] { type Source = S }
    def apply[Arrows <: HList](implicit cs: CommonSource[Arrows]): Aux[Arrows, cs.Source] = cs

    implicit def singleCommonSource[H <: Arrow, S](implicit hSource: Types.Aux[H, S, _]): CommonSource.Aux[H :: HNil, S] =
      instance.asInstanceOf[CommonSource.Aux[H :: HNil, S]]

    implicit def multipleCommonSource[H <: Arrow, T <: HList, HS, TS, S](
      implicit
      notSingle: T <:!< HNil,
      hSource: Types.Aux[H, HS, _],
      tSource: CommonSource.Aux[T, TS],
      lub: Lub[HS, TS, S]
    ): CommonSource.Aux[H :: T, S] = instance.asInstanceOf[CommonSource.Aux[H :: T, S]]

    private lazy val instance = new CommonSource[HList]{}
  }

  trait Targets[Arrows <: HList] { type Targets <: HList }
  object Targets {
    type Aux[Arrows <: HList, Ts <: HList] = Targets[Arrows] { type Targets = Ts }
    def apply[Arrows <: HList](implicit t: Targets[Arrows]): Aux[Arrows, t.Targets] = t

    implicit def singleTarget[H <: Arrow, HT](implicit h: H <:< Arrow.Inv[_, HT]): Targets.Aux[H :: HNil, HT :: HNil] =
      instance.asInstanceOf[Targets.Aux[H :: HNil, HT :: HNil]]
    implicit def multipleTargets[H <: Arrow, T <: HList, HT](
      implicit
      notSingle: T <:!< HNil,
      h: H <:< Arrow.Inv[_, HT],
      t: Targets[T]
    ): Targets.Aux[H :: T, HT :: t.Targets] = instance.asInstanceOf[Targets.Aux[H :: T, HT :: t.Targets]]

    private lazy val instance = new Targets[HList] {}
  }
}


// TODO: is it a functor? should I rename it?
/** A typeclass supporting ???. */
trait Functor[From <: Arrow, To <: Arrow] extends DepFn1[From] { type Out <: Arrow }
object Functor {
  type Aux[From <: Arrow, To <: Arrow, Out0 <: Arrow] = Functor[From, To] { type Out = Out0 }
  def apply[From <: Arrow, To <: Arrow](implicit functor: Functor[From, To]): Functor.Aux[From, To, functor.Out] = functor
  def map[From <: Arrow](from: From): PartialApplication[From] = new PartialApplication(from)

  def define[From <: Arrow, To <: Arrow]: DefinitionBuilder[From, To] = DefinitionBuilder.asInstanceOf[DefinitionBuilder[From, To]]

  protected class DefinitionBuilder[From <: Arrow, To <: Arrow] {
    def apply[R <: Arrow](map: From => R): Functor.Aux[From, To, R] =
      new Functor[From, To] {
        type Out = R
        def apply(t: From): R = map(t)
      }
  }
  private object DefinitionBuilder extends DefinitionBuilder[Arrow, Arrow]

  protected class PartialApplication[From <: Arrow](from: From) {
    def to[To <: Arrow](implicit functor: Functor[From, To]): functor.Out = functor(from)
  }

  implicit def compositionFunctor[From <: Arrow, To <: Arrow, FromF <: Arrow, ToF <: Arrow, FromG <: Arrow, ToG <: Arrow](
    implicit
    composition: From <:< Arrow.Composition[FromF, FromG],
    fF: Lazy[Functor.Aux[FromF, To, ToF]],
    fG: Lazy[Functor.Aux[FromG, To, ToG]],
    compose: Arrow.Compose[ToF, ToG],
    lowPriority: LowPriority
   ): Functor.Aux[From, To, compose.Out] = define[From, To](t => compose(fF.value(t.F), fG.value(t.G)))

  implicit def splitFunctor[From <: Arrow, To <: Arrow, Arrows <: HList, Mapped <: HList](
    implicit
    isSplit: From <:< Arrow.Split[Arrows],
    fmap: Lazy[FMapHList.Aux[Arrows, To, Mapped]],
    split: Arrow.Split.Splitter[Mapped],
    lowPriority: LowPriority
  ): Functor.Aux[From, To, split.Out] = define[From, To](t => split(fmap.value(t.arrows)))


  trait FMapHList[Arrows <: HList, To <: Arrow] extends DepFn1[Arrows] { type Out <: HList }
  object FMapHList {
    type Aux[Arrows <: HList, To <: Arrow, Mapped <: HList] = FMapHList[Arrows, To] { type Out = Mapped }
    def apply[Arrows <: HList, To <: Arrow](implicit fmap: FMapHList[Arrows, To]): Aux[Arrows, To, fmap.Out] = fmap

    implicit def fmapHnil[To <: Arrow]: FMapHList.Aux[HNil, To, HNil] = fmapHnilInstance.asInstanceOf[FMapHList.Aux[HNil, To, HNil]]
    private lazy val fmapHnilInstance = new FMapHList[HNil, Arrow] {
      type Out = HNil
      def apply(t: HNil): HNil = HNil
    }

    implicit def fmapHcons[H <: Arrow, T <: HList, To <: Arrow, H1 <: Arrow, T1 <: HList](
      implicit
      mapH: Lazy[Functor.Aux[H, To, H1]],
      mapT: Lazy[FMapHList.Aux[T, To, T1]]
    ): FMapHList.Aux[H :: T, To, H1 :: T1] =
      new FMapHList[H :: T, To] {
        type Out = H1 :: T1
        def apply(t: H :: T): H1 :: T1 = mapH.value(t.head) :: mapT.value(t.tail)
      }
  }
}