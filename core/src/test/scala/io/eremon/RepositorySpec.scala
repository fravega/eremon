package io.eremon

import scala.concurrent.ExecutionContext

import org.scalatest.time.{ Second, Seconds, Span }

import com.whisk.docker.scalatest.DockerTestKit
import com.whisk.docker.impl.spotify.DockerKitSpotify

import test._
import criteria._

/**
 * Verifies the Repository behaves as expected
 */
class RepositorySpec extends Spec
  with DockerTestKit
  with DockerKitSpotify
  with DockerMongoDBTestKit {
  import RepositorySpec._

  implicit val pc = PatienceConfig(Span(20, Seconds), Span(1, Second))

  lazy implicit val database = makeConnector

  lazy val testRepository = new TestRepository(database)

  before {
    clean(database)
  }

  override def afterAll(): Unit = {
    close(database)
    super.afterAll()

  }

  "mongodb node" should "be ready with log line checker" in {
    isContainerReady(mongodbContainer).futureValue shouldBe true
    mongodbContainer.getPorts().futureValue.get(27017) should not be empty
    mongodbContainer.getIpAddresses().futureValue should not be Seq.empty
  }

  "A ReactiveRepository" should "support insertion and find by id" in {
    val id = ID.generate()
    testRepository.insert(Test("Linus Torvalds", 47, id)).map(_.ok).futureValue shouldBe true
    testRepository.findById(id).futureValue shouldBe Some(Test("Linus Torvalds", 47, id))
  }

  it should "support deletion" in {
    val id = ID.generate()
    testRepository.insert(Test("Linus Torvalds", 47, id)).map(_.ok).futureValue shouldBe true
    testRepository.removeById(id).map(_.ok).futureValue shouldBe true
    testRepository.findById(id).futureValue shouldBe None
  }

  it should "support find by a field" in {
    import Typed._
    val id = ID.generate()
    testRepository.insert(Test("Linus Torvalds", 47, id)).map(_.ok).futureValue shouldBe true
    testRepository.findOne(criteria[Test](_.name) === "Linus Torvalds").futureValue shouldBe Some(Test("Linus Torvalds", 47, id))
  }
}

object RepositorySpec {
  import reactivemongo.bson._
  import reactivemongo.bson.Macros.Annotations._

  case class Test(name: String, age: Int, @Key("_id") id: ID = ID.generate())

  implicit val testReader: BSONDocumentReader[Test] = Macros.reader[Test]
  implicit val testWriter: BSONDocumentWriter[Test] = Macros.writer[Test]

  class TestRepository(database: MongoDB)(implicit ec: ExecutionContext) extends ReactiveRepository[Test](
    database,
    "test",
    testReader,
    testWriter,
    ec) {

  }

}