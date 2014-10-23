package web

import spray.routing.HttpService

trait AssetsService extends HttpService {
  def assets = path("") {
    getFromResource("webapp/index.html")
  } ~ getFromResourceDirectory("webapp")
}
