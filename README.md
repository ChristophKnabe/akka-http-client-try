# Akka HTTP Client Try #

Requests one web page by Akka Actors and Akka HTTP, consumes the response by Akka Streams, and displays the HTML source code of it.

## Contribution policy ##

Contributions via GitHub pull requests are gladly accepted from their original author. Along with any pull requests, please state that the contribution is your original work and that you license the work to the project under the project's open source license. Whether or not you state this explicitly, by submitting any copyrighted material via pull request, email, or other means you agree to license the material under the project's open source license and warrant that you have the legal authority to do so.

## License ##

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").

## Authors ##

* Christoph Knabe modified the following example at 2017-07-03
* http://doc.akka.io/docs/akka-http/current/scala/http/client-side/request-level.html

## Documentation ##

### Usage ###

`sbt run` 

This will request the page at the fixed URL, and print its source text.
A Reactive Stream is used when scanning the web page, as the page could be very long. This occurs in method `ClientActor.receive` by `entity.dataBytes`.

