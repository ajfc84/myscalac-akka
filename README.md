# MyScalacAkka

## Installation Steps

1. Install JDK
2. Install SBT
3. git clone https://github.com/ajfc84/myscalac-akka.git

## Running the server
4. cd /<<i>pathtoprojectfolder<i>>/myscalac-akka
5. sbt run
   
## Stoping the server
6. press \<\<Enter\>\> (stop the server)

### Project Description
 This project is a small API of one only endpoint: "http://localhost:8080/org/<organization>/contributors"  that 
 essentially connects to the GitHub API (api.github.com) and retrieves the number of contributions per
 contributor for a specified organization. The "organization" of the request is specified in the second segment of the
 path, <<i>organization</b>>.
  This Scala project is based in Akka. It uses the new Akka-typed-Actor library, Akka Streams 
 Akka HTTP, Akka Testkit and ScalaTest.
  I used mostly FP style but left one class in OOP style just for the sake of example.