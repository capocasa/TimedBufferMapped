
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
      var inputch, run, time;
      inputch = inputArray[i].copy;
      run = inputch[0] > 0;
      time = Sweep.kr(1,run>0); 
      bufspecch[1..].pairsDo { |ch, b|
        RecordBufT.kr(inputch[ch], b, run:run);
        inputch[ch] = time;
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
        outch[ch] = PlayBufT.kr(1, b, rate * run, run, startPos) * run;
        out[i] = outch;
      };
    }
    ^out;
  }
}


+ Buffer {
  *allocMapped {
    arg server, polyphony, numChannels, channels = #[], frames = 16384, cframes = 16777216;
    var bufspec;
    bufspec = polyphony.collect {
      var bufspecch;
      bufspecch = Array.new(2*channels.size+1);
      bufspecch.add(Buffer.alloc(server, frames, numChannels+1));
      channels.do { |ch|
        bufspecch.add(ch);
        bufspecch.add(Buffer.alloc(server, cframes, 2));
      };
      bufspecch;
    };
    ^bufspec;
  }

  *readMapped {
    arg server, path;
    var bufspec, files, channels, buffers, sounds, polyphony, headerFormat, fileExtension;
    files=PathName(path).files.select{|p|p.fileName[0]==$p};
    #polyphony, channels, fileExtension = files.first.fileName.split($.);
    channels = channels.split($-).collect{|c|c.asInteger};
    headerFormat = fileExtension.toLower;
    polyphony = files.collect{|f|f.fileName[1..f.fileName.indexOf($.)-1].asInteger}.maxItem+1;
    sounds = files.collect{|f|SoundFile(f.fullPath)};
    bufspec=sounds.collect {|read|
      var write, line, spec, writeposch;
//"read %".format(read.path).postln;
      read.openRead;
      write = SoundFile(thisProcess.platform.defaultTempDir +/+ "tmp.n" ++ 2147483647.rand ++ $.++ fileExtension);
//"write %".format(write.path).postln;
      write.numChannels = read.numChannels;
      write.sampleFormat = "float";
      write.headerFormat = headerFormat;
      write.openWrite;
      line = FloatArray.newClear(read.numChannels);
      spec = channels.collect {|ch|
        var writech;
        writech = SoundFile(thisProcess.platform.defaultTempDir +/+ "tmp.w" ++ 2147483647.rand ++ $. ++ fileExtension);
        writech.headerFormat = headerFormat;
        writech.sampleFormat = "float";
//"openWrite %".format(writech.path).postln;
        writech.numChannels = 2;
        writech.openWrite;
        [ch, writech];
      }.flatten;

//1.postln;
      writeposch = Array.fill(channels.maxItem+1, 0);
      while { read.readData(line); line.size > 0 }{
        var v, p, time;
//"pre %".format(line.asCompileString).postln;
        if (line[1] > 0) {
//2.postln;
          spec.pairsDo { |ch, writech, i|
            v = line[ch+1];
            p = path +/+ $v ++ v ++ $.++ fileExtension;

            line[ch+1] = writeposch[ch];
//3.postln;

            SoundFile.use(p) { |readch|
              var chunk;
//"channel path % exists % frames %".format(p, File.exists(p), readch.numFrames).postln;
              time = 0;
              chunk = FloatArray.newClear(64);
              while { readch.readData(chunk); chunk.size > 0 }{
                writech.writeData(chunk);
//"concatenating channel % data: %".format(ch, chunk.asCompileString).postln;
                chunk.pairsDo {|t|time = time + t};
              };
              writeposch.atInc(ch, time);
//[\frames, frames, frames - readch.numFrames, readch.numFrames].postln;
            };
//4.postln;
          };
        };
//"post %".format(line.asCompileString).postln;
        write.writeData(line);
//6.postln;
      };
      read.close;
      write.close;
      spec.pairsDo {|ch, writech, i|
//"y readTimed % isOpen %".format(writech.path, writech.isOpen).postln;
        writech.close;
        spec[i+1] = Buffer.readTimed(server, writech.path);
        fork {
          server.sync;
//"%".format(writech.path).postln;
          //"soxi %".format(writech.path).unixCmdGetStdOut.postln;
          File.delete(writech.path);
          writech.path = nil;
        };
      };
//"x readTimed %".format(write.path).postln;
      [Buffer.readTimed(server, write.path)]++spec;
    };
    fork {
      server.sync;
      bufspec.collect{|b|
        File.delete(b[0].path);
        b[0].path = nil
      };
    };
//7.postln;
    ^bufspec;
  }
}


+ Array {
  writeMapped {
    arg path, headerFormat = "aiff";
    var buffer, base, count, server, soundExtension, channels;
    server=this[0][0].server;
    soundExtension = headerFormat.toLower;
    count = 1;

//6.postln;
    //if (frames.asArray.every({|e|e==1})) {
    //  "No frames, not saving %".format(path).warn;
    //  this.yield;
    //};
    path.mkdir;
//5.postln;
    channels = [];
    this[0][1..].pairsDo{|ch|channels=channels.add(ch)};
    this.do { |bufspecch, i|
      var read, write, pathch, tmp, line, id;
      fork {
//4.postln;
        pathch=path+/+"p"++i++$.++channels.join("-")++$.++soundExtension;
        tmp = thisProcess.platform.defaultTempDir +/+ "map" ++ 2147483647.rand++$.++soundExtension;
        bufspecch[0].writeTimed(tmp, headerFormat);
        server.sync;
//"a %".format(File.exists(tmp)).postln;
        read = SoundFile.openRead(tmp);
        write = SoundFile(pathch);
        write.numChannels = read.numChannels;
        write.headerFormat = headerFormat;
        write.sampleFormat = "float";
        write.openWrite;
        line = FloatArray.newClear(read.numChannels);
//"3 % %".format(tmp, read.numChannels).postln;
        id = 0;
        while { read.readData(line); line.size > 0 } {
//2.postln;
//line.postln;
          bufspecch[1..].pairsDo {|ch, b, x|
            var r, w, p, start, length;
            if (line[1] > 0) {  // Don't save signals for pause notes
//9.postln;
              id = 16777215.rand; // largest integer that accurately casts to float
              start = line[ch+1]; // ch is output channel index, buffers have additional time channel at beginning, so ch+1
              length = line[0];
              p = path +/+ "v"++id ++ $. ++ soundExtension;
//b.updateInfo;server.sync;"read polyphony % id % of length % start % to % buffer frames %".format(i,id,length,start,p,b.numFrames).postln;
              b.writeTimed(p, headerFormat, start, length);

//b.getn(0, 20, action:{|x|"read array for id % %".format(id, x.asCompileString).postln});server.sync;
              line[ch+1] = id;
//"id %".format(id).postln;
            };
          };
//1.postln;
          write.writeData(line);
        };
        server.sync; 
//7.postln;
        read.close;
        write.close;
        File.delete(tmp);
      };
//8.postln;

    }
  }
}


