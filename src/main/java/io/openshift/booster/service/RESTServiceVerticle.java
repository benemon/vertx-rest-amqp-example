package io.openshift.booster.service;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import io.openshift.common.CommonConstants;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class RESTServiceVerticle extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger("RESTServiceVerticle");

	private static final String REST_STATUS = "REST API Status: %s";
	private static final String OK = "OK";
	private static final String NOT_OK = "Not OK";

	private boolean restOnline = false;

	@Override
	public void start() throws Exception {

		Router router = Router.router(vertx);

		HealthCheckHandler healthCheckHandler = HealthCheckHandler.create(vertx).register("server-online",
				fut -> fut.complete(restOnline ? Status.OK() : Status.KO()));

		router.get("/api/health/readiness").handler(rc -> rc.response().end(OK));
		router.get("/api/health/liveness").handler(healthCheckHandler);
		router.get("/api/publish").handler(this::publishData);

		HttpServer server = vertx.createHttpServer().requestHandler(router::accept)
				.listen(config().getInteger("http.port", 8080), ar -> {
					if (ar.succeeded()) {
						log.info(String.format(REST_STATUS, OK));
						restOnline = ar.succeeded();
					} else {
						log.error(String.format(REST_STATUS, ar.cause().getMessage()));
					}
				});
	}

	/**
	 * Send the data to AMQ
	 * 
	 * @param rc
	 */
	private void publishData(RoutingContext rc) {
		if (restOnline != true) {
			rc.response().setStatusCode(400).putHeader(CONTENT_TYPE, "text/plain").end(NOT_OK);
			return;
		}

		String data = rc.request().getParam("data");

		try {

			JsonObject messagePayload = new JsonObject();
			messagePayload.put("host", rc.request().host());
			messagePayload.put("body", data);

			vertx.eventBus().send(CommonConstants.VERTX_EVENTBUS_DATA_ADDRESS, messagePayload);

			log.info("Message delivered to event bus");

			rc.response().setStatusCode(200).putHeader(CONTENT_TYPE, "text/plain").end(OK);

		} catch (Exception e1) {

			log.error("Oops", e1);
			rc.response().setStatusCode(400).putHeader(CONTENT_TYPE, "text/plain").end(e1.getMessage());

		}
	}

}
