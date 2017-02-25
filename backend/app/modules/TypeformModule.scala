package modules

import com.google.inject.AbstractModule
import services.TypeformService

class TypeformModule extends AbstractModule {
  def configure() = {
    bind(classOf[TypeformService]).asEagerSingleton
  }
}
