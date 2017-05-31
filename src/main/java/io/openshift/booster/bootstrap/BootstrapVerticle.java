package io.openshift.booster.bootstrap;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import io.openshift.booster.messaging.AMQProducerVerticle;
import io.openshift.booster.service.RESTServiceVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;

public class BootstrapVerticle extends AbstractVerticle {

	private static final Logger log = LoggerFactory.getLogger("BootstrapVerticle");

	@Override
	public void start(Future<Void> startFuture) throws Exception {

		super.start(startFuture);

		vertx.deployVerticle(RESTServiceVerticle.class.getName(), res -> {
		});

		vertx.deployVerticle(AMQProducerVerticle.class.getName(), new DeploymentOptions().setWorker(true), res -> {
		});

	}

}
