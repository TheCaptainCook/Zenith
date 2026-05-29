import { NestFactory } from '@nestjs/core';
import { WorkerModule } from './worker/worker.module';
import { Logger } from '@nestjs/common';

async function bootstrap() {
  // We use createMicroservice to run as a pure background process (no HTTP server)
  const app = await NestFactory.createMicroservice(WorkerModule, {});
  await app.listen();
  
  const logger = new Logger('WorkerMicroservice');
  logger.log('Zenith Background Worker is running and listening to Redis queues...');
}
bootstrap();
