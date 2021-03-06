package org.zalando.nakadi.client

import java.net.URI
import java.util.function.Supplier
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.typesafe.scalalogging.LazyLogging

object KlientBuilder{
  def apply(endpoint: URI = null,
            port: Int = DEFAULT_PORT,
            securedConnection: Boolean = false,
            tokenProvider: () => String = null,
            objectMapper: Option[ObjectMapper] = None) =
      new KlientBuilder(endpoint, port, securedConnection, tokenProvider, objectMapper)

  private val DEFAULT_PORT = 8080    // having such a default is probably a bad idea. Rework later. tbd AKa120216
}

class KlientBuilder private (val endpoint: URI = null,
                             val port: Int,
                             val securedConnection: Boolean,
                             val tokenProvider: () => String = null,
                             val objectMapper: Option[ObjectMapper] = None)
  extends LazyLogging
{
  def this() = this(null, KlientBuilder.DEFAULT_PORT, false, null, None)

  private def checkNotNull[T](subject: T): T =
                                   if(Option(subject).isEmpty) throw new NullPointerException else subject


  private def checkState[T](subject: T, predicate: (T) => Boolean, msg: String): T =
                                   if(predicate(subject)) subject else throw new IllegalStateException()


  def withEndpoint(endpoint: URI): KlientBuilder =
                                new KlientBuilder(
                                  checkNotNull(endpoint),
                                  port,
                                  securedConnection,
                                  tokenProvider,
                                  objectMapper)


  def withTokenProvider(tokenProvider: () => String): KlientBuilder =
                                new KlientBuilder(
                                  endpoint,
                                  port,
                                  securedConnection,
                                  checkNotNull(tokenProvider),
                                  objectMapper)


  def withJavaTokenProvider(tokenProvider: Supplier[String]) = withTokenProvider(() => tokenProvider.get())


  // TODO param check
  def withPort(port: Int): KlientBuilder =
                                new KlientBuilder(
                                  endpoint,
                                  port,
                                  securedConnection,
                                  tokenProvider,
                                  objectMapper)


  def withSecuredConnection(securedConnection: Boolean = true) =
                                new KlientBuilder(
                                  endpoint,
                                  port,
                                  securedConnection,
                                  tokenProvider,
                                  objectMapper)


  def withObjectMapper(objectMapper: Option[ObjectMapper]): KlientBuilder =  {
    if(objectMapper.isDefined) objectMapper.get.registerModule(new DefaultScalaModule)

    new KlientBuilder(endpoint, port, securedConnection, tokenProvider, objectMapper)
  }


  def defaultObjectMapper: ObjectMapper = {
    val mapper = new ObjectMapper
    mapper.registerModule(new DefaultScalaModule)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES)
    mapper.addHandler(new DeserializationProblemHandler() {
      override def handleUnknownProperty(ctxt: DeserializationContext,
                                         jp: JsonParser, deserializer:
                                         JsonDeserializer[_],
                                         beanOrClass: AnyRef,
                                         propertyName: String): Boolean = {
        logger.warn(s"unknown property occurred in JSON representation: [beanOrClass=$beanOrClass, property=$propertyName]")
        true
      }
    })
    mapper
  }


  def build(): Klient =
      new KlientImpl(
                    checkState(endpoint, (s: URI) => Option(s).isDefined, "endpoint is not set -> try withEndpoint()"),
                    checkState(port, (s: Int) => port > 0, s"port $port is invalid"),
                    securedConnection,
                    checkState(tokenProvider, (s: () => String) => Option(s).isDefined,
                               "tokenProvider is not set -> try withTokenProvider()"),
                    objectMapper.getOrElse(defaultObjectMapper))


  def buildJavaClient(): Client = new JavaClientImpl(build())


  override def toString = s"KlientBuilder($endpoint, $tokenProvider, $objectMapper)"
}
