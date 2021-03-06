package app

import java.util.concurrent.atomic.AtomicInteger

import org.asynchttpclient.ws.{WebSocket, WebSocketListener, WebSocketUpgradeHandler}
import utest._

object ExampleTests extends TestSuite{


  def test[T](example: cask.main.BaseMain)(f: String => T): T = {
    val server = io.undertow.Undertow.builder
      .addHttpListener(8080, "localhost")
      .setHandler(example.defaultHandler)
      .build
    server.start()
    val res =
      try f("http://localhost:8080")
      finally server.stop()
    res
  }

  val tests = Tests{
    'Websockets - test(Websockets){ host =>
      @volatile var out = List.empty[String]
      val client = org.asynchttpclient.Dsl.asyncHttpClient();
      try{

        // 4. open websocket
        val ws: WebSocket = client.prepareGet("ws://localhost:8080/connect/haoyi")
          .execute(new WebSocketUpgradeHandler.Builder().addWebSocketListener(
            new WebSocketListener() {

              override def onTextFrame(payload: String, finalFragment: Boolean, rsv: Int) {
                out = payload :: out
              }

              def onOpen(websocket: WebSocket) = ()

              def onClose(websocket: WebSocket, code: Int, reason: String) = ()

              def onError(t: Throwable) = ()
            }).build()
          ).get()

        // 5. send messages
        ws.sendTextFrame("hello")
        ws.sendTextFrame("world")
        ws.sendTextFrame("")
        Thread.sleep(100)
        out ==> List("haoyi world", "haoyi hello")

        var error: String = ""
        val cli2 = client.prepareGet("ws://localhost:8080/connect/nobody")
          .execute(new WebSocketUpgradeHandler.Builder().addWebSocketListener(
            new WebSocketListener() {

              def onOpen(websocket: WebSocket) = ()

              def onClose(websocket: WebSocket, code: Int, reason: String) = ()

              def onError(t: Throwable) = {
                error = t.toString
              }
            }).build()
          ).get()

        assert(error.contains("403"))

      } finally{
        client.close()
      }
    }

    'Websockets2000 - test(Websockets){ host =>
      @volatile var out = List.empty[String]
      val closed = new AtomicInteger(0)
      val client = org.asynchttpclient.Dsl.asyncHttpClient();
      val ws = Seq.fill(2000)(client.prepareGet("ws://localhost:8080/connect/haoyi")
        .execute(new WebSocketUpgradeHandler.Builder().addWebSocketListener(
          new WebSocketListener() {

            override def onTextFrame(payload: String, finalFragment: Boolean, rsv: Int) = {
              ExampleTests.synchronized {
                out = payload :: out
              }
            }

            def onOpen(websocket: WebSocket) = ()

            def onClose(websocket: WebSocket, code: Int, reason: String) = {
              closed.incrementAndGet()
            }

            def onError(t: Throwable) = ()
          }).build()
        ).get())

      try{
        // 5. send messages
        ws.foreach(_.sendTextFrame("hello"))

        Thread.sleep(1500)
        out.length ==> 2000

        ws.foreach(_.sendTextFrame("world"))

        Thread.sleep(1500)
        out.length ==> 4000
        closed.get() ==> 0

        ws.foreach(_.sendTextFrame(""))

        Thread.sleep(1500)
        closed.get() ==> 2000

      }finally{
        client.close()
      }
    }

  }
}
