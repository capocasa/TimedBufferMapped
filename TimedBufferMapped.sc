
// This adds support for recording mapped controls

RecordBufM {
  *ar {
    thisMethod.notYetImplemented;
  }

  *kr {
    arg inputArray, bufspec=#[0], run=1.0, doneAction=0;

    if (inputArray.size != bufspec.size) {
      RecordBufMError("inputArray size is %, but bufspec size is %, need be equal".format(inputArray.size, bufspec.size)).throw;
    };

    inputArray = inputArray.copy;

    bufspec.do{|bufspecch, i|
      var rec, inputch, run, phase;
      inputch = inputArray[i].copy;
      run = inputch[0] > 0;
      phase = Phasor.kr(1, run, 0, 2147483647);
      bufspecch[1..].pairsDo { |ch, b|
        RecordBuf.kr(inputch[ch], b, run:run);
        inputch[ch] = phase;
      };
      inputArray[i] = inputch;
    };
    ^RecordBufT.kr(inputArray, bufspec.collect{|bufspecch|bufspecch[0]}, run, doneAction);
  }
}
RecordBufMError : Error {}

PlayBufM {
  *ar {
    thisMethod.notYetImplemented;
  }

  *kr {
    arg numChannels, bufspec = #[0], rate=1.0, trigger=1.0, startPos=0.0, doneAction=0;
    var play, out;

    out = PlayBufT.kr(numChannels, bufspec.collect{|bufspecch|bufspecch[0]}, rate, trigger, startPos, doneAction);

    bufspec.do { |bufspecch, i|
      var run;
      run = out[i][0] > 0;

      bufspecch[1..].pairsDo { |ch, b|
        var outch;
        outch = out[i].copy; // avoid buffer coloring error
        startPos = outch[ch];
        outch[ch] = PlayBuf.kr(1, b, rate * run, run, startPos) * run;
        out[i] = outch;
      };
    }
    ^out;
  }
}




