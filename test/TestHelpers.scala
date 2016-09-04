import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.Injector
import scala.reflect.ClassTag
  
trait Inject {
  lazy val injector = (new GuiceApplicationBuilder).injector
  def inject[T: ClassTag]: T = injector.instanceOf[T]
}
